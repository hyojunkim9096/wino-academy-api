package com.wino.academyapi.domain.admin.team.entity;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 팀 기본 정보(팀장 포함)
 * DDL: team_group
 */
@Entity
@Table(
        name = "team_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_team_code", columnNames = {"team_code"}),
                @UniqueConstraint(name = "uq_team_workloc_name", columnNames = {"work_location", "team_name"})
        },
        indexes = {
                @Index(name = "idx_team_status", columnList = "status"),
                @Index(name = "idx_team_workloc", columnList = "work_location"),
                @Index(name = "idx_team_leader", columnList = "leader_admin_id")
        }
)
public class TeamGroup {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="team_code", length=64)
    private String teamCode;

    @Column(name="team_name", nullable=false, length=120)
    private String teamName;

    @Column(name="description", length=255)
    private String description;

    @Column(name="work_location", length=100)
    private String workLocation;

    @Column(name="status", nullable=false, length=20)
    private String status = "ACTIVE"; // ACTIVE/INACTIVE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="leader_admin_id", foreignKey=@ForeignKey(name="fk_team_leader"))
    private AdminUser leader; // nullable

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="created_by", foreignKey=@ForeignKey(name="fk_team_created_by"))
    private AdminUser createdBy; // nullable

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="updated_by", foreignKey=@ForeignKey(name="fk_team_updated_by"))
    private AdminUser updatedBy; // nullable

    @PrePersist
    public void onInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    public void onUpdate() { updatedAt = LocalDateTime.now(); }

    // getters/setters
    public Long getId() { return id; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public AdminUser getLeader() { return leader; }
    public void setLeader(AdminUser leader) { this.leader = leader; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public AdminUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AdminUser createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public AdminUser getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(AdminUser updatedBy) { this.updatedBy = updatedBy; }
}