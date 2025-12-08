package organization.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Статус операции: IN_PROGRESS, SUCCESS, FAILED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    // Пользователь, запустивший импорт (Администратор)
    @Column(nullable = false)
    private String userName;

    // Время начала операции
    @Column(nullable = false)
    private LocalDateTime startDate = LocalDateTime.now();

    // Время завершения операции
    private LocalDateTime endDate;

    // Число успешно добавленных объектов (только для SUCCESS)
    private Integer objectCount;

    // Сообщение об ошибке (для FAILED)
    @Lob // Используем @Lob для потенциально длинного сообщения об ошибке
    @Column(length = 4000)
    private String errorMessage;
}