package preserve.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author fdse
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 308837338487438338L;
    private UUID userId;
    private String userName;
    private String password;

    private int gender;

    private int documentType;

    private String documentNum;

    private String email;

}
