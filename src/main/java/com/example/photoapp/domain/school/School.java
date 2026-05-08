package com.example.photoapp.domain.school;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "school")
public class School extends Auditable {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_email")
    private String contactEmail;

    protected School() {
        // JPA
    }

    public School(String name, String address, String contactEmail) {
        this.name = name;
        this.address = address;
        this.contactEmail = contactEmail;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
