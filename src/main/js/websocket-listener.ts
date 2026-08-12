import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

export interface Registration {
  route: string;
  callback: (message: IMessage) => void;
}

let stompClient: Client | null = null;
const subscriptions: Record<string, StompSubscription> = {};

// Build the native WebSocket URL for the STOMP endpoint from the current page location.
function brokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/glm`;
}

function register(registrations: Registration[]): void {
  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 5000,
    onConnect: () => {
      registrations.forEach((registration) => {
        subscriptions[registration.route] = client.subscribe(registration.route, registration.callback);
      });
    },
  });
  stompClient = client;
  client.activate();
}

function unregister(registrations: Registration[]): void {
  registrations.forEach((registration) => {
    const subscription = subscriptions[registration.route];
    if (subscription) {
      subscription.unsubscribe();
      delete subscriptions[registration.route];
    }
  });
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
}

export default { register, unregister };
