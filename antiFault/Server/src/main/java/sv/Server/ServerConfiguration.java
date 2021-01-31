package sv.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
public class ServerConfiguration {

    @Value("${primary}")
    private boolean primary;

    @Value("${replicaId}")
    private int replicaId;

    @Value("${active}")
    private boolean active;

    @Value("${isReady}")
    private boolean isReady;

    private int requestId;

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public void setReplicaId(int replicaId) {
        this.replicaId = replicaId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }

    @Override
    public String toString() {
        return "SeverConfiguration{" +
                "primary=" + primary +
                ", replicaId=" + replicaId +
                ", active=" + active +
                ", isReady=" + isReady +
                '}';
    }
}


