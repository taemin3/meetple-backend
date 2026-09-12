import { performance } from 'node:perf_hooks';

export class StompConnection {
  constructor(url, accessToken, index) {
    this.url = url;
    this.accessToken = accessToken;
    this.index = index;
    this.buffer = '';
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.socket = new WebSocket(this.url);
      const timeout = setTimeout(() => reject(new Error(`STOMP connect timeout: ${this.index}`)), 10000);
      this.socket.addEventListener('open', () => this.frame('CONNECT', {
        'accept-version': '1.2',
        host: this.url.host,
        Authorization: `Bearer ${this.accessToken}`,
        'heart-beat': '10000,10000',
      }));
      this.socket.addEventListener('message', (event) => this.consume(String(event.data), resolve, reject, timeout));
      this.socket.addEventListener('error', () => reject(new Error(`WebSocket error: ${this.index}`)));
    });
  }

  consume(chunk, resolve, reject, timeout) {
    this.buffer += chunk;
    let end;
    while ((end = this.buffer.indexOf('\0')) >= 0) {
      const raw = this.buffer.slice(0, end).replace(/^\n+/, '');
      this.buffer = this.buffer.slice(end + 1);
      if (!raw.trim()) continue;
      const separator = raw.indexOf('\n\n');
      const headerLines = raw.slice(0, separator).split('\n');
      const command = headerLines.shift();
      const headers = Object.fromEntries(headerLines.map((line) => {
        const colon = line.indexOf(':');
        return [line.slice(0, colon), line.slice(colon + 1)];
      }));
      const body = raw.slice(separator + 2);
      if (command === 'CONNECTED') {
        clearTimeout(timeout);
        resolve();
      } else if (command === 'MESSAGE') {
        try {
          const parsed = JSON.parse(body);
          if (headers.destination?.includes('/queue/chat/errors')) {
            this.onErrorMessage?.(parsed);
          } else {
            this.onMessage?.(parsed.data, performance.now());
          }
        } catch (error) {
          this.onErrorMessage?.({ parseError: error.message, body });
        }
      } else if (command === 'ERROR') {
        clearTimeout(timeout);
        this.onErrorMessage?.({ headers, body });
        reject(new Error(`STOMP ERROR on client ${this.index}: ${body}`));
      }
    }
  }

  subscribe(destination, id) {
    this.frame('SUBSCRIBE', { destination, id, ack: 'auto' });
  }

  send(destination, body) {
    if (this.socket.readyState !== WebSocket.OPEN) throw new Error('WebSocket is not open.');
    const json = JSON.stringify(body);
    this.frame('SEND', {
      destination,
      'content-type': 'application/json',
      'content-length': Buffer.byteLength(json),
    }, json);
  }

  frame(command, headers = {}, body = '') {
    const lines = [command, ...Object.entries(headers).map(([key, value]) => `${key}:${value}`), '', body];
    this.socket.send(`${lines.join('\n')}\0`);
  }

  close() {
    if (!this.socket || this.socket.readyState >= WebSocket.CLOSING) return Promise.resolve();
    return new Promise((resolve) => {
      const timeout = setTimeout(resolve, 1000);
      this.socket.addEventListener('close', () => {
        clearTimeout(timeout);
        resolve();
      }, { once: true });
      this.frame('DISCONNECT');
      this.socket.close();
    });
  }
}
