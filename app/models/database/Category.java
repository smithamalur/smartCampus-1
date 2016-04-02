package models.database;

import com.avaje.ebean.Model;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Created by Sharmila on 3/20/2016.
 */

/**
 * This Table Would be prepopulated
 */
@Data
@Entity
@Table(name = "category")
public class Category extends Model {

    @Id
    @GeneratedValue
    private int id;

    private String name;

    private String description;

}
