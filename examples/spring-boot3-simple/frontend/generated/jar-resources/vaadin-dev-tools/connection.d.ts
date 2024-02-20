import { Product } from './License';
import { ComponentReference } from './component-util';
export declare enum ConnectionStatus {
    ACTIVE = "active",
    INACTIVE = "inactive",
    UNAVAILABLE = "unavailable",
    ERROR = "error"
}
export declare class Connection extends Object {
    static HEARTBEAT_INTERVAL: number;
    status: ConnectionStatus;
    webSocket?: WebSocket;
    constructor(url?: string);
    onHandshake(): void;
    onReload(): void;
    onUpdate(_path: string, _content: string): void;
    onConnectionError(_: string): void;
    onStatusChange(_: ConnectionStatus): void;
    onMessage(message: any): void;
    handleMessage(msg: any): void;
    handleError(msg: any): void;
    setActive(yes: boolean): void;
    setStatus(status: ConnectionStatus): void;
    send(command: string, data: any): void;
    setFeature(featureId: string, enabled: boolean): void;
    sendTelemetry(browserData: any): void;
    sendLicenseCheck(product: Product): void;
    sendShowComponentCreateLocation(component: ComponentReference): void;
    sendShowComponentAttachLocation(component: ComponentReference): void;
}
