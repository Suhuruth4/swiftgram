import nacl from 'tweetnacl'
import { decodeBase64, encodeBase64 } from 'tweetnacl-util'

export type KeyPair = { publicKey: string; secretKey: string }

export const generateIdentityKeyPair = (): KeyPair => {
  const pair = nacl.box.keyPair()
  return {
    publicKey: encodeBase64(pair.publicKey),
    secretKey: encodeBase64(pair.secretKey),
  }
}

export const generateChatKey = (): string => {
  return encodeBase64(nacl.randomBytes(32))
}

export const encryptChatKeyForUser = (
  chatKeyBase64: string,
  recipientPublicKeyBase64: string,
  senderSecretKeyBase64: string
) => {
  const chatKey = decodeBase64(chatKeyBase64)
  const recipientPublicKey = decodeBase64(recipientPublicKeyBase64)
  const senderSecretKey = decodeBase64(senderSecretKeyBase64)
  const nonce = nacl.randomBytes(nacl.box.nonceLength)
  const boxed = nacl.box(chatKey, nonce, recipientPublicKey, senderSecretKey)
  return {
    encryptedKey: encodeBase64(boxed),
    nonce: encodeBase64(nonce),
  }
}

export const decryptChatKey = (
  encryptedKeyBase64: string,
  nonceBase64: string,
  senderPublicKeyBase64: string,
  recipientSecretKeyBase64: string
) => {
  const boxed = decodeBase64(encryptedKeyBase64)
  const nonce = decodeBase64(nonceBase64)
  const senderPublicKey = decodeBase64(senderPublicKeyBase64)
  const recipientSecretKey = decodeBase64(recipientSecretKeyBase64)
  const opened = nacl.box.open(boxed, nonce, senderPublicKey, recipientSecretKey)
  if (!opened) return null
  return encodeBase64(opened)
}

export const encryptMessage = (plaintext: string, chatKeyBase64: string) => {
  const key = decodeBase64(chatKeyBase64)
  const nonce = nacl.randomBytes(nacl.secretbox.nonceLength)
  const msg = new TextEncoder().encode(plaintext)
  const boxed = nacl.secretbox(msg, nonce, key)
  return {
    ciphertext: encodeBase64(boxed),
    nonce: encodeBase64(nonce),
  }
}

export const decryptMessage = (ciphertextBase64: string, nonceBase64: string, chatKeyBase64: string) => {
  const key = decodeBase64(chatKeyBase64)
  const nonce = decodeBase64(nonceBase64)
  const boxed = decodeBase64(ciphertextBase64)
  const opened = nacl.secretbox.open(boxed, nonce, key)
  if (!opened) return null
  return new TextDecoder().decode(opened)
}

export const encryptBytes = (data: Uint8Array, chatKeyBase64: string) => {
  const key = decodeBase64(chatKeyBase64)
  const nonce = nacl.randomBytes(nacl.secretbox.nonceLength)
  const boxed = nacl.secretbox(data, nonce, key)
  return {
    ciphertext: boxed,
    nonce: encodeBase64(nonce),
  }
}

export const decryptBytes = (ciphertext: Uint8Array, nonceBase64: string, chatKeyBase64: string) => {
  const key = decodeBase64(chatKeyBase64)
  const nonce = decodeBase64(nonceBase64)
  const opened = nacl.secretbox.open(ciphertext, nonce, key)
  if (!opened) return null
  return opened
}
