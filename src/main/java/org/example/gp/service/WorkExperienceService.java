package org.example.gp.service;

import org.example.gp.dto.ExperienceResult;
import org.example.gp.dto.DateRange;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkExperienceService {

    public Period calculateExperience(LocalDate start, LocalDate end) {
        return Period.between(start, end);
    }

    public ExperienceResult calculateNOI(List<DateRange> ranges) {

        if (ranges.isEmpty()) {
            return new ExperienceResult(0, 0, 0);
        }

        // SORT
        ranges.sort(Comparator.comparing(DateRange::getStart));

        // MERGE OVERLAP
        List<DateRange> merged = new ArrayList<>();

        DateRange current = ranges.get(0);

        for (int i = 1; i < ranges.size(); i++) {

            DateRange next = ranges.get(i);

            if (!next.getStart().isAfter(current.getEnd().plusDays(1))) {

                current = new DateRange(
                        current.getStart(),
                        current.getEnd().isAfter(next.getEnd())
                                ? current.getEnd()
                                : next.getEnd()
                );

            } else {

                merged.add(current);
                current = next;
            }
        }

        merged.add(current);

        int totalYears = 0;
        int totalMonths = 0;
        int totalDays = 0;

        for (DateRange r : merged) {

            LocalDate start = r.getStart();
            LocalDate end = r.getEnd();

            int years = end.getYear() - start.getYear();
            int months = end.getMonthValue() - start.getMonthValue();
            int days = end.getDayOfMonth() - start.getDayOfMonth();

            // borrow days
            if (days < 0) {
                days += 30;
                months--;
            }

            // borrow months
            if (months < 0) {
                months += 12;
                years--;
            }

            totalYears += years;
            totalMonths += months;
            totalDays += days;
        }

        // normalize
        if (totalDays >= 30) {
            totalMonths += totalDays / 30;
            totalDays = totalDays % 30;
        }

        if (totalMonths >= 12) {
            totalYears += totalMonths / 12;
            totalMonths = totalMonths % 12;
        }

        return new ExperienceResult(
                totalYears,
                totalMonths,
                totalDays
        );
    }
}
