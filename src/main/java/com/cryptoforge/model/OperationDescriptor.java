package com.cryptoforge.model;

import java.util.List;
import java.util.Objects;

/**
 * Descriptor of an operation available in CryptoCarver.
 */
public final class OperationDescriptor {

    public enum Status {
        STABLE, EXPERIMENTAL, PLANNED
    }

    public enum SecretRisk {
        NONE, LOW, HIGH, EXTREME
    }

    private final String id;
    private final String title;
    private final String category;
    private final String subtitle;
    private final String icon;
    private final Status status;
    private final SecretRisk secretRisk;
    private final String navigationPath;
    private final List<String> aliases;

    public OperationDescriptor(String id, String title, String category, String subtitle,
                               String icon, Status status, SecretRisk secretRisk, String navigationPath, List<String> aliases) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ID cannot be null or blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title cannot be null or blank");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Category cannot be null or blank");
        if (navigationPath == null || navigationPath.isBlank()) throw new IllegalArgumentException("NavigationPath cannot be null or blank");

        this.id = id;
        this.title = title;
        this.category = category;
        this.subtitle = subtitle != null ? subtitle : "";
        this.icon = icon != null ? icon : "";
        this.status = status != null ? status : Status.STABLE;
        this.secretRisk = secretRisk != null ? secretRisk : SecretRisk.NONE;
        this.navigationPath = navigationPath;
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSubtitle() { return subtitle; }
    public String getIcon() { return icon; }
    public Status getStatus() { return status; }
    public SecretRisk getSecretRisk() { return secretRisk; }
    public String getNavigationPath() { return navigationPath; }
    public List<String> getAliases() { return aliases; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperationDescriptor)) return false;
        OperationDescriptor that = (OperationDescriptor) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
