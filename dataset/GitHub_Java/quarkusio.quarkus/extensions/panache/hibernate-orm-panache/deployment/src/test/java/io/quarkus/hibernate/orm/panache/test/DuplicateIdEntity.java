package io.quarkus.hibernate.orm.panache.test;

import javax.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

public class DuplicateIdEntity extends PanacheEntity {
    @Id
    public String customId;
}
