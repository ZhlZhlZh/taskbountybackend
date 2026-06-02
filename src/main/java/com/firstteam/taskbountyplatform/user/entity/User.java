package com.firstteam.taskbountyplatform.user.entity;

import com.firstteam.taskbountyplatform.common.enums.AccountRole;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User entity - users table.
 * Represents platform users (students/staff).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_no", unique = true, nullable = false, length = 50)
    private String studentNo;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Column(name = "grade", nullable = true, length = 20)
    private String grade;

    @Column(name = "college", length = 100)
    private String college;

    @Column(name = "academy", length = 100)
    private String academy;

    @Column(name = "nickname", unique = true, nullable = false, length = 50)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl = "/avatars/default.png";

    @Column(name = "announcement", length = 200, nullable = true)
    private String announcement;

    @Column(name = "email", nullable = true, length = 100)
    private String email;

    @Column(name = "phone", nullable = true, length = 20)
    private String phone;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore = 80;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private UserStatus accountStatus = UserStatus.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role = AccountRole.USER;

    @Column(name = "frozen_until", nullable = true)
    private LocalDateTime frozenUntil;

    @Column(name = "freeze_reason", nullable = true, length = 200)
    private String freezeReason;

    @Column(name = "graduated", nullable = false)
    private Boolean graduated = false;

    @Column(name = "graduation_freeze_count", nullable = false)
    private Integer graduationFreezeCount = 0;

    @Column(name = "credit_reset_used", nullable = false)
    private Boolean creditResetUsed = false;

    @Column(name = "last_login_time", nullable = true)
    private LocalDateTime lastLoginTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== Constructors ==========

    public User() {
    }

    public User(Long id, String studentNo, String realName, String grade, String college,
                String academy, String nickname, String avatarUrl, String announcement,
                String email, String phone, Integer creditScore, UserStatus accountStatus,
                AccountRole role, LocalDateTime frozenUntil, String freezeReason,
                Boolean graduated, Integer graduationFreezeCount, Boolean creditResetUsed,
                LocalDateTime lastLoginTime, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.studentNo = studentNo;
        this.realName = realName;
        this.grade = grade;
        this.college = college;
        this.academy = academy;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.announcement = announcement;
        this.email = email;
        this.phone = phone;
        this.creditScore = creditScore;
        this.accountStatus = accountStatus;
        this.role = role;
        this.frozenUntil = frozenUntil;
        this.freezeReason = freezeReason;
        this.graduated = graduated;
        this.graduationFreezeCount = graduationFreezeCount;
        this.creditResetUsed = creditResetUsed;
        this.lastLoginTime = lastLoginTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.avatarUrl == null) {
            this.avatarUrl = "/avatars/default.png";
        }
        if (this.creditScore == null) {
            this.creditScore = 80;
        }
        if (this.accountStatus == null) {
            this.accountStatus = UserStatus.NORMAL;
        }
        if (this.role == null) {
            this.role = AccountRole.USER;
        }
        if (this.graduated == null) {
            this.graduated = false;
        }
        if (this.graduationFreezeCount == null) {
            this.graduationFreezeCount = 0;
        }
        if (this.creditResetUsed == null) {
            this.creditResetUsed = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Manual Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getAcademy() {
        return academy;
    }

    public void setAcademy(String academy) {
        this.academy = academy;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public UserStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(UserStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public LocalDateTime getFrozenUntil() {
        return frozenUntil;
    }

    public void setFrozenUntil(LocalDateTime frozenUntil) {
        this.frozenUntil = frozenUntil;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public Boolean getGraduated() {
        return graduated;
    }

    public void setGraduated(Boolean graduated) {
        this.graduated = graduated;
    }

    public Integer getGraduationFreezeCount() {
        return graduationFreezeCount;
    }

    public void setGraduationFreezeCount(Integer graduationFreezeCount) {
        this.graduationFreezeCount = graduationFreezeCount;
    }

    public Boolean getCreditResetUsed() {
        return creditResetUsed;
    }

    public void setCreditResetUsed(Boolean creditResetUsed) {
        this.creditResetUsed = creditResetUsed;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", studentNo='" + studentNo + '\'' +
                ", realName='" + realName + '\'' +
                ", grade='" + grade + '\'' +
                ", college='" + college + '\'' +
                ", academy='" + academy + '\'' +
                ", nickname='" + nickname + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", announcement='" + announcement + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", creditScore=" + creditScore +
                ", accountStatus=" + accountStatus +
                ", role=" + role +
                ", frozenUntil=" + frozenUntil +
                ", freezeReason='" + freezeReason + '\'' +
                ", graduated=" + graduated +
                ", graduationFreezeCount=" + graduationFreezeCount +
                ", creditResetUsed=" + creditResetUsed +
                ", lastLoginTime=" + lastLoginTime +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
