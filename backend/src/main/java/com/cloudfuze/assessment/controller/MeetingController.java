package com.cloudfuze.assessment.controller;

import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.security.CurrentUser;
import com.cloudfuze.assessment.service.MeetingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** Part 3, the live meeting with the AI participant. */
@RestController
@RequestMapping("/api/test/meeting")
public class MeetingController {

    private final MeetingService meetings;
    private final CurrentUser current;

    public MeetingController(MeetingService meetings, CurrentUser current) {
        this.meetings = meetings;
        this.current = current;
    }

    @GetMapping
    public Dtos.MeetingState state(@RequestHeader("X-Session-Key") String key) {
        return meetings.state(current.user(), key);
    }

    /** A one-time secret for the browser to open the live voice call. */
    @PostMapping("/session")
    public Dtos.MeetingSession session(@Valid @RequestBody Dtos.SessionRequest r) throws Exception {
        return meetings.session(current.user(), r.sessionKey());
    }

    /** The transcript so far, sent by the browser as the conversation grows. */
    @PostMapping("/transcript")
    public Dtos.MeetingState transcript(@Valid @RequestBody Dtos.MeetingTranscript r) {
        return meetings.transcript(current.user(), r.sessionKey(), r.turns());
    }

    @PostMapping("/end")
    public Dtos.MeetingState end(@Valid @RequestBody Dtos.MeetingEnd r) {
        return meetings.end(current.user(), r.sessionKey(), r.satisfied());
    }
}
