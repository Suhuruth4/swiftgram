import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export const createStompClient = (token: string, url: string) => {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${url.replace(/\/$/, '')}/ws`),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    debug: () => {},
    reconnectDelay: 2000,
  })
  return client
}
