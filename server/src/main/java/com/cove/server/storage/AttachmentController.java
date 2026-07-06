package com.cove.server.storage;

import com.cove.server.auth.AuthContext;
import com.cove.server.message.MessageRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentRepository attachmentRepository;
    private final MessageRepository messageRepository;
    private final StorageService storageService;

    public AttachmentController(AttachmentRepository attachmentRepository,
                                MessageRepository messageRepository,
                                StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.messageRepository = messageRepository;
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "originalName", required = false) String originalName,
                                     @RequestParam(value = "mimeType", required = false) String mimeType,
                                     @RequestParam(value = "encNonce", required = false) String encNonce,
                                     @RequestParam(value = "encAlg", required = false) String encAlg) throws Exception {
        UUID userId = AuthContext.requireUserId();
        UUID id = UUID.randomUUID();
        String objectKey = "attachments/" + id;
        storageService.putObject(objectKey, file.getInputStream(), file.getSize(),
            file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);

        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setUploaderId(userId);
        attachment.setObjectKey(objectKey);
        attachment.setOriginalName(originalName != null ? originalName : file.getOriginalFilename());
        attachment.setMimeType(mimeType != null ? mimeType : file.getContentType());
        attachment.setSize(file.getSize());
        attachment.setEncNonce(encNonce);
        attachment.setEncAlg(encAlg);
        attachmentRepository.save(attachment);

        return AttachmentResponse.from(attachment);
    }

    @GetMapping("/{id}")
    public void download(@PathVariable("id") UUID id, HttpServletResponse response) throws Exception {
        UUID userId = AuthContext.requireUserId();
        Attachment attachment = attachmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        ensureCanAccess(attachment, userId);
        response.setContentType(attachment.getMimeType() != null ? attachment.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        try (InputStream input = storageService.getObject(attachment.getObjectKey())) {
            input.transferTo(response.getOutputStream());
        }
    }

    @GetMapping("/{id}/meta")
    public AttachmentResponse meta(@PathVariable("id") UUID id) {
        UUID userId = AuthContext.requireUserId();
        Attachment attachment = attachmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        ensureCanAccess(attachment, userId);
        return AttachmentResponse.from(attachment);
    }

    private void ensureCanAccess(Attachment attachment, UUID userId) {
        if (userId.equals(attachment.getUploaderId())) {
            return;
        }
        if (messageRepository.existsAccessibleAttachment(attachment.getId(), userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment is not available to this user");
    }

    public record AttachmentResponse(UUID id, String originalName, String mimeType, long size, String encNonce, String encAlg) {
        public static AttachmentResponse from(Attachment attachment) {
            return new AttachmentResponse(attachment.getId(), attachment.getOriginalName(), attachment.getMimeType(),
                attachment.getSize(), attachment.getEncNonce(), attachment.getEncAlg());
        }
    }
}
