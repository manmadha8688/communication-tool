package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Marks per part: Teams message 20, Email 35, Meeting 45. They add up to exactly 100, so the
 * overall is simply the sum of the three parts.
 */
@Component
public class MarksPolicy {

    private final Map<Part, Double> weights = new EnumMap<>(Part.class);

    public MarksPolicy(@Value("${app.test.marks.teams:20}") double teams,
                       @Value("${app.test.marks.email:35}") double email,
                       @Value("${app.test.marks.meeting:45}") double meeting) {
        weights.put(Part.TEAMS, teams);
        weights.put(Part.EMAIL, email);
        weights.put(Part.MEETING, meeting);
    }

    public Map<Part, Double> maxMarks(Collection<Part> present) {
        Map<Part, Double> out = new EnumMap<>(Part.class);
        for (Part p : present) {
            out.put(p, (double) Math.round(weights.get(p)));
        }
        return out;
    }
}
