import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;

/**
 * Represents a single item loaded from or stored to database.
 */
public class Item {
    @BsonId
    private Integer id;
    private Date created;
    private double value;

    public Item() {

    }

    public Item(int id, Date created, double value) {
        this.id = id;
        this.created = created;
        this.value = value;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
