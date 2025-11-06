package com.wino.academyapi.domain.admin.team.entity;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 팀 구성원(직원/강사)
 * DDL: team_member
 */
@Entity
@Table(
        name = "team_member",
        uniqueConstraints = @UniqueConstraint(name="uq_team_member", columnNames = {"team_id","admin_id"}),
        indexes = {
                @Index(name="idx_tm_team", columnList = "team_id"),
                @Index(name="idx_tm_admin", columnList = "admin_id"),
                @Index(name="idx_tm_active", columnList = "active_yn"),
                @Index(name="idx_tm_role", columnList = "role_in_team")
        }
)
public class TeamMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    @JoinColumn(name="team_id", foreignKey=@ForeignKey(name="fk_tm_team"))
    private TeamGroup team;

    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    @JoinColumn(name="admin_id", foreignKey=@ForeignKey(name="fk_tm_admin"))
    private AdminUser admin;

    @Column(name="role_in_team", nullable=false, length=20)
    private String roleInTeam = "MEMBER"; // LEADER/MEMBER

    @Column(name="active_yn", nullable=false, length=1)
    private String activeYn = "Y"; // Y/N

    @Column(name="joined_at", nullable=false)
    private LocalDateTime joinedAt;

    @Column(name="left_at")
    private LocalDateTime leftAt;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="created_by", foreignKey=@ForeignKey(name="fk_tm_created_by"))
    private AdminUser createdBy;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="updated_by", foreignKey=@ForeignKey(name="fk_tm_updated_by"))
    private AdminUser updatedBy;

    @PrePersist
    public void onInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (joinedAt == null) joinedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (activeYn == null) activeYn = "Y";
        if (roleInTeam == null) roleInTeam = "MEMBER";
    }

    @PreUpdate
    public void onUpdate() { updatedAt = LocalDateTime.now(); }

    // getters/setters
    public Long getId() { return id; }
    public TeamGroup getTeam() { return team; }
    public void setTeam(TeamGroup team) { this.team = team; }
    public AdminUser getAdmin() { return admin; }
    public void setAdmin(AdminUser admin) { this.admin = admin; }
    public String getRoleInTeam() { return roleInTeam; }
    public void setRoleInTeam(String roleInTeam) { this.roleInTeam = roleInTeam; }
    public String getActiveYn() { return activeYn; }
    public void setActiveYn(String activeYn) { this.activeYn = activeYn; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public AdminUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AdminUser createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public AdminUser getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(AdminUser updatedBy) { this.updatedBy = updatedBy; }
}