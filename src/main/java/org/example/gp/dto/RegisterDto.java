package org.example.gp.dto;

public class RegisterDto {

    private String username;
    private String password;
    private String confirmPassword;
    private String officeName;   // Ime на кантората (само при регистрация на нова кантора)
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getOfficeName() { return officeName; }
    public void setOfficeName(String officeName) { this.officeName = officeName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
