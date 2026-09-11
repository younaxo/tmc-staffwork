package su.twomc.staffwork.service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.storage.StaffRepository;

public final class StaffService {
    private static final Pattern RANK_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final StaffRepository repository;
    private final Executor executor;
    private final Clock clock;
    private volatile Set<String> allowedRanks;

    public StaffService(StaffRepository repository, Executor executor, Clock clock, Set<String> allowedRanks) {
        this.repository = repository;
        this.executor = executor;
        this.clock = clock;
        this.allowedRanks = Set.copyOf(allowedRanks);
    }

    public void updateAllowedRanks(Set<String> ranks) {
        allowedRanks = Set.copyOf(ranks);
    }

    public CompletableFuture<OperationResult<StaffMember>> add(UUID uuid, String name, String rank, UUID addedBy) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String normalizedRank = normalizeRank(rank);
                    if (!isValidRank(normalizedRank)) {
                        return OperationResult.failure("error.invalid-rank");
                    }
                    if (repository.findStaff(uuid).isPresent()) {
                        return OperationResult.failure("staff.already-exists");
                    }
                    StaffMember member = new StaffMember(
                            uuid, name, normalizedRank, true, clock.instant(), addedBy, clock.instant());
                    repository.saveStaff(member);
                    return OperationResult.success("staff.added", member);
                },
                executor);
    }

    public CompletableFuture<OperationResult<StaffMember>> setRank(UUID uuid, String rank) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String normalized = normalizeRank(rank);
                    if (!isValidRank(normalized)) {
                        return OperationResult.failure("error.invalid-rank");
                    }
                    Optional<StaffMember> current = repository.findStaff(uuid);
                    if (current.isEmpty()) {
                        return OperationResult.failure("staff.not-found");
                    }
                    StaffMember updated = current.get().withRank(normalized);
                    repository.saveStaff(updated);
                    return OperationResult.success("staff.rank-set", updated);
                },
                executor);
    }

    public CompletableFuture<OperationResult<StaffMember>> setEnabled(UUID uuid, boolean enabled) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Optional<StaffMember> current = repository.findStaff(uuid);
                    if (current.isEmpty()) {
                        return OperationResult.failure("staff.not-found");
                    }
                    StaffMember updated = current.get().withEnabled(enabled);
                    repository.saveStaff(updated);
                    return OperationResult.success(enabled ? "staff.enabled" : "staff.disabled", updated);
                },
                executor);
    }

    public CompletableFuture<OperationResult<Void>> remove(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> repository.deleteStaff(uuid)
                        ? OperationResult.success("staff.removed", null)
                        : OperationResult.failure("staff.not-found"),
                executor);
    }

    public CompletableFuture<Optional<StaffMember>> find(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> repository.findStaff(uuid), executor);
    }

    public CompletableFuture<Optional<StaffMember>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> repository.findStaffByName(name), executor);
    }

    public CompletableFuture<List<StaffMember>> list() {
        return CompletableFuture.supplyAsync(repository::findAllStaff, executor);
    }

    public CompletableFuture<Void> recordSeen(UUID uuid, String name) {
        return CompletableFuture.runAsync(
                () -> repository
                        .findStaff(uuid)
                        .ifPresent(member -> repository.saveStaff(member.seenAs(name, clock.instant()))),
                executor);
    }

    public static String normalizeRank(String rank) {
        return rank == null ? "" : rank.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isValidRank(String rank) {
        return RANK_ID.matcher(rank).matches() && allowedRanks.contains(rank);
    }
}
