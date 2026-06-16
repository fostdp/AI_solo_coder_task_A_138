class AlertWebSocket {
  constructor(onAlert) {
    this.onAlert = onAlert;
    this.ws = null;
    this._closed = false;
    this._connect();
  }

  _connect() {
    const url = `ws://${window.location.host}/ws/alerts`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.onAlert(data);
      } catch (e) {
        console.error('Failed to parse WebSocket message', e);
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error', error);
    };

    this.ws.onclose = () => {
      if (!this._closed) {
        setTimeout(() => this._connect(), 3000);
      }
    };
  }

  close() {
    this._closed = true;
    if (this.ws) {
      this.ws.close();
    }
  }
}

export { AlertWebSocket };
