package io.quarkus.it.panache;

import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@XmlRootElement(name = "JAXBEntity")
@XmlAccessorType(XmlAccessType.NONE)
public class JAXBEntity extends PanacheEntity {

    @XmlAttribute(name = "Named")
    public String namedAnnotatedProp;

    @XmlTransient
    public String transientProp;

    @XmlAttribute
    public String defaultAnnotatedProp;

    public String unAnnotatedProp;
}
