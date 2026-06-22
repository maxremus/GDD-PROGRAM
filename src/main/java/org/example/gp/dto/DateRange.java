package org.example.gp.dto;

import jakarta.persistence.Entity;
import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DateRange {

    private LocalDate start;
    private LocalDate end;
}
