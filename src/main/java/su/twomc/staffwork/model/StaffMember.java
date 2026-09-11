package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StaffMember(
        UUID uuid,
        String lastKnownName,
        String rank,
        boolean enabled,
        Instant addedAt,
        UUID addedBy,
        Instant lastSeenAt) {

    public StaffMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(addedAt, "addedAt");
        if (lastKnownName.length() > 16) {
            throw new IllegalArgumentException("Ник не может быть длиннее 16 символов");
        }
    }

    public StaffMember withRank(String newRank) {
        return new StaffMember(uuid, lastKnownName, newRank, enabled, addedAt, addedBy, lastSeenAt);
    }

    public StaffMember withEnabled(boolean newEnabled) {
        return new StaffMember(uuid, lastKnownName, rank, newEnabled, addedAt, addedBy, lastSeenAt);
    }

    public StaffMember seenAs(String name, Instant at) {
        return new StaffMember(uuid, name, rank, enabled, addedAt, addedBy, at);
    }
}
