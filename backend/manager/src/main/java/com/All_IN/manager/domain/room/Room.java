package com.All_IN.manager.domain.room;

import com.All_IN.manager.domain.SqlDateTime;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "room_table")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Where(clause = "DELETE_AT IS NULL")
@SQLDelete(sql = "UPDATE ROOM_TABLE SET DELETE_AT = CURRENT_TIMESTAMP where ROOM_ID = ?")
@Getter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Column(name = "publisher_id")
    private Long publisherId;

    @Embedded
    private SqlDateTime.createAndDeleteAt sqlDateTime;


    private Room(Long publisherId) {
        this.publisherId = publisherId;
        this.sqlDateTime = new SqlDateTime.createAndDeleteAt();
    }

    public static Room relatedFrom(Long publisherId) {
        return new Room(publisherId);
    }

}
