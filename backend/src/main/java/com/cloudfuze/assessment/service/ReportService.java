package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.Part;
import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.entity.Answer;
import com.cloudfuze.assessment.entity.Attempt;
import com.cloudfuze.assessment.repository.AnswerRepository;
import com.cloudfuze.assessment.repository.AttemptRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The manager's Excel report: one row per candidate with every detail, the mark for each exam and
 * the overall, then a summary by team. Formatted to be read and printed as it is.
 */
@Service
public class ReportService {

    private static final String[] PARTS = {"TEAMS", "EMAIL", "MEETING"};
    private static final String[] PART_LABEL = {"Teams message", "Email", "Meeting"};

    private final AdminService admin;
    private final AttemptRepository attempts;
    private final AnswerRepository answers;

    public ReportService(AdminService admin, AttemptRepository attempts, AnswerRepository answers) {
        this.admin = admin;
        this.attempts = attempts;
        this.answers = answers;
    }

    @Transactional(readOnly = true)
    public byte[] excel(String team) throws Exception {
        List<Dtos.AttemptRow> rows = admin.rows(team);
        Map<Long, Map<String, Answer>> byAttempt = new TreeMap<>();
        for (Attempt a : attempts.findAll()) {
            Map<String, Answer> m = new TreeMap<>();
            for (Answer x : answers.findByAttemptIdOrderByIdAsc(a.getId())) m.put(x.getPart().name(), x);
            byAttempt.put(a.getId(), m);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            candidatesSheet(wb, st, rows, byAttempt, team);
            teamSheet(wb, st, rows);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ sheet 1: every candidate

    private void candidatesSheet(XSSFWorkbook wb, Styles st, List<Dtos.AttemptRow> rows,
                                 Map<Long, Map<String, Answer>> byAttempt, String team) {
        Sheet s = wb.createSheet("Candidates");
        String[] head = {"#", "Name", "Employee ID", "Email", "Team", "Role", "Status", "How it ended",
                "Started", "Submitted", "Time taken (min)", "Strikes",
                "Teams question", "Teams /20", "Email question", "Email /35", "Meeting question", "Meeting /45",
                "Meeting outcome", "Overall /100"};
        int[] width = {5, 24, 13, 32, 18, 22, 13, 26, 18, 18, 11, 9, 14, 11, 14, 11, 16, 12, 18, 13};

        title(s, st, "Neutara CommuniQ: candidate report",
                (team == null || team.isBlank() ? "All teams" : "Team: " + team) + "   |   Generated "
                        + ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) + " IST"
                        + "   |   " + rows.size() + (rows.size() == 1 ? " candidate" : " candidates"),
                head.length);
        Row h = s.createRow(3);
        h.setHeightInPoints(30);
        for (int i = 0; i < head.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(head[i]);
            c.setCellStyle(i == head.length - 1 ? st.headStrong : st.head);
            s.setColumnWidth(i, width[i] * 256);
        }

        int r = 4;
        int n = 1;
        for (Dtos.AttemptRow row : rows) {
            Row x = s.createRow(r);
            x.setHeightInPoints(20);
            boolean alt = n % 2 == 0;
            Map<String, Answer> ans = row.attemptId() == null ? Map.of() : byAttempt.getOrDefault(row.attemptId(), Map.of());
            int c = 0;
            num(x, c++, n, st.cell(alt, true));
            text(x, c++, row.name(), st.cellBold(alt));
            text(x, c++, row.employeeId(), st.cell(alt, false));
            text(x, c++, row.email(), st.cell(alt, false));
            text(x, c++, row.team(), st.cell(alt, false));
            text(x, c++, row.jobRole(), st.cell(alt, false));
            text(x, c++, statusText(row.status()), st.status(row.status(), alt));
            text(x, c++, row.endReason(), st.cell(alt, false));
            text(x, c++, row.startedAt(), st.cell(alt, false));
            text(x, c++, row.submittedAt(), st.cell(alt, false));
            if (row.durationSeconds() == null) text(x, c++, "", st.cell(alt, true));
            else num(x, c++, Math.round(row.durationSeconds() / 60.0), st.cell(alt, true));
            num(x, c++, row.violations(), row.violations() > 0 ? st.strike(alt) : st.cell(alt, true));
            for (String p : PARTS) {
                Answer a = ans.get(p);
                text(x, c++, a == null ? "" : a.getQuestion().getCode(), st.cell(alt, true));
                Dtos.PartScore ps = row.parts().stream().filter(q -> q.part().equals(p)).findFirst().orElse(null);
                if (ps == null || ps.marks() == null) text(x, c++, row.attemptId() == null ? "" : "—", st.cell(alt, true));
                else num(x, c++, Math.round(ps.marks()), st.mark(alt));
            }
            Answer m = ans.get(Part.MEETING.name());
            text(x, c++, m == null || m.getTranscriptJson() == null ? "" : m.getMeetingSatisfied() == null ? "Ended by time"
                    : m.getMeetingSatisfied() ? "Satisfied" : "Not satisfied", st.cell(alt, false));
            if (row.totalMarks() == null) text(x, c, row.attemptId() == null ? "" : "Marking…", st.overallEmpty(alt));
            else num(x, c, Math.round(row.totalMarks()), st.overall(alt));
            r++;
            n++;
        }
        if (rows.isEmpty()) {
            Row x = s.createRow(r);
            text(x, 1, "No candidates yet.", st.note);
        }
        s.createFreezePane(2, 4);
        if (!rows.isEmpty()) {
            s.setAutoFilter(new CellRangeAddress(3, r - 1, 0, head.length - 1));
        }
        Row note = s.createRow(r + 1);
        text(note, 1, "Marks are whole numbers. Overall = Teams + Email + Meeting (out of 100). "
                + "Strikes = tab switches / leaving full screen / leaving the window; the third ends the test.", st.note);
        s.getPrintSetup().setLandscape(true);
        s.setFitToPage(true);
        s.getPrintSetup().setFitWidth((short) 1);
        s.getPrintSetup().setFitHeight((short) 0);
    }

    // ------------------------------------------------------------------ sheet 2: by team

    private void teamSheet(XSSFWorkbook wb, Styles st, List<Dtos.AttemptRow> rows) {
        Sheet s = wb.createSheet("Team summary");
        String[] head = {"Team", "Candidates", "Submitted", "In progress", "Terminated", "Not started",
                "Avg Teams /20", "Avg Email /35", "Avg Meeting /45", "Avg overall /100", "Highest", "Lowest"};
        int[] width = {24, 12, 12, 12, 12, 12, 14, 14, 16, 16, 10, 10};
        title(s, st, "Team summary", "Averages are over candidates whose marking is complete.", head.length);
        Row h = s.createRow(3);
        h.setHeightInPoints(30);
        for (int i = 0; i < head.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(head[i]);
            c.setCellStyle(i == 9 ? st.headStrong : st.head);
            s.setColumnWidth(i, width[i] * 256);
        }
        Map<String, List<Dtos.AttemptRow>> teams = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Dtos.AttemptRow r : rows) teams.computeIfAbsent(r.team() == null ? "No team" : r.team(), k -> new ArrayList<>()).add(r);
        teams.put("All teams", rows);
        int r = 4;
        int n = 0;
        for (Map.Entry<String, List<Dtos.AttemptRow>> e : teams.entrySet()) {
            if (e.getKey().equals("All teams")) continue;
            writeTeam(s, st, r++, e.getKey(), e.getValue(), n++ % 2 == 1, false);
        }
        writeTeam(s, st, r, "All teams", rows, false, true);
        s.createFreezePane(1, 4);
    }

    private void writeTeam(Sheet s, Styles st, int rowIdx, String name, List<Dtos.AttemptRow> list, boolean alt, boolean total) {
        Row x = s.createRow(rowIdx);
        x.setHeightInPoints(20);
        List<Dtos.AttemptRow> marked = list.stream().filter(r -> r.totalMarks() != null).toList();
        CellStyle base = total ? st.totalRow : st.cell(alt, true);
        int c = 0;
        text(x, c++, name, total ? st.totalRow : st.cellBold(alt));
        num(x, c++, list.size(), base);
        num(x, c++, list.stream().filter(r -> r.status().equals("SUBMITTED")).count(), base);
        num(x, c++, list.stream().filter(r -> r.status().equals("IN_PROGRESS")).count(), base);
        num(x, c++, list.stream().filter(r -> r.status().equals("TERMINATED")).count(), base);
        num(x, c++, list.stream().filter(r -> r.attemptId() == null).count(), base);
        for (String p : PARTS) {
            double avg = marked.stream().map(r -> r.parts().stream().filter(q -> q.part().equals(p)).findFirst().orElse(null))
                    .filter(q -> q != null && q.marks() != null).mapToDouble(Dtos.PartScore::marks).average().orElse(Double.NaN);
            if (Double.isNaN(avg)) text(x, c++, "—", base); else num(x, c++, Math.round(avg), total ? st.totalRow : st.mark(alt));
        }
        double avg = marked.stream().mapToDouble(Dtos.AttemptRow::totalMarks).average().orElse(Double.NaN);
        if (Double.isNaN(avg)) text(x, c++, "—", total ? st.totalRow : st.overallEmpty(alt));
        else num(x, c++, Math.round(avg), total ? st.totalRow : st.overall(alt));
        double hi = marked.stream().mapToDouble(Dtos.AttemptRow::totalMarks).max().orElse(Double.NaN);
        double lo = marked.stream().mapToDouble(Dtos.AttemptRow::totalMarks).min().orElse(Double.NaN);
        if (Double.isNaN(hi)) text(x, c++, "—", base); else num(x, c++, Math.round(hi), base);
        if (Double.isNaN(lo)) text(x, c, "—", base); else num(x, c, Math.round(lo), base);
    }

    // ------------------------------------------------------------------ helpers

    private static void title(Sheet s, Styles st, String title, String sub, int cols) {
        Row t = s.createRow(0);
        t.setHeightInPoints(28);
        text(t, 0, title, st.title);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
        Row u = s.createRow(1);
        text(u, 0, sub, st.subtitle);
        s.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));
    }

    private static String statusText(String s) {
        return switch (s) {
            case "SUBMITTED" -> "Submitted";
            case "IN_PROGRESS" -> "In progress";
            case "TERMINATED" -> "Terminated";
            case "NOT_STARTED" -> "Not started";
            case "DETAILS_PENDING" -> "Details pending";
            default -> s;
        };
    }

    private static void text(Row r, int c, String v, CellStyle s) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        cell.setCellStyle(s);
    }

    private static void num(Row r, int c, double v, CellStyle s) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(s);
    }

    /** The workbook's look: CloudFuze blue header, light striping, the overall column emphasised. */
    private static final class Styles {
        final CellStyle title, subtitle, head, headStrong, note, totalRow;
        private final XSSFWorkbook wb;
        private final CellStyle[] plain = new CellStyle[4];
        private final CellStyle[] bold = new CellStyle[2];
        private final CellStyle[] marks = new CellStyle[2];
        private final CellStyle[] overall = new CellStyle[2];
        private final CellStyle[] overallEmpty = new CellStyle[2];
        private final CellStyle[] strike = new CellStyle[2];
        private final java.util.Map<String, CellStyle> status = new java.util.HashMap<>();

        Styles(XSSFWorkbook wb) {
            this.wb = wb;
            Font tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 15);
            tf.setColor(IndexedColors.WHITE.getIndex());
            title = wb.createCellStyle();
            title.setFont(tf);
            fill(title, "0129AC");
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font sf = wb.createFont();
            sf.setItalic(true);
            sf.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle = wb.createCellStyle();
            subtitle.setFont(sf);

            head = header("01186B");
            headStrong = header("0129AC");

            Font nf = wb.createFont();
            nf.setItalic(true);
            nf.setFontHeightInPoints((short) 9);
            nf.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            note = wb.createCellStyle();
            note.setFont(nf);

            Font bf = wb.createFont();
            bf.setBold(true);
            totalRow = wb.createCellStyle();
            totalRow.setFont(bf);
            fill(totalRow, "DCE4FA");
            border(totalRow);
            totalRow.setAlignment(HorizontalAlignment.CENTER);

            for (int i = 0; i < 2; i++) {
                boolean alt = i == 1;
                plain[i] = base(alt, false);
                plain[i + 2] = base(alt, true);
                bold[i] = base(alt, false);
                Font f = wb.createFont();
                f.setBold(true);
                bold[i].setFont(f);
                marks[i] = base(alt, true);
                Font mf = wb.createFont();
                mf.setBold(true);
                ((org.apache.poi.xssf.usermodel.XSSFFont) mf).setColor(new XSSFColor(hex("0129AC"), null));
                marks[i].setFont(mf);
                overall[i] = base(alt, true);
                Font of = wb.createFont();
                of.setBold(true);
                of.setFontHeightInPoints((short) 12);
                of.setColor(IndexedColors.WHITE.getIndex());
                overall[i].setFont(of);
                fill(overall[i], "0129AC");
                overallEmpty[i] = base(alt, true);
                strike[i] = base(alt, true);
                Font rf = wb.createFont();
                rf.setBold(true);
                rf.setColor(IndexedColors.RED.getIndex());
                strike[i].setFont(rf);
            }
        }

        CellStyle cell(boolean alt, boolean center) {
            return plain[(alt ? 1 : 0) + (center ? 2 : 0)];
        }

        CellStyle cellBold(boolean alt) {
            return bold[alt ? 1 : 0];
        }

        CellStyle mark(boolean alt) {
            return marks[alt ? 1 : 0];
        }

        CellStyle overall(boolean alt) {
            return overall[alt ? 1 : 0];
        }

        CellStyle overallEmpty(boolean alt) {
            return overallEmpty[alt ? 1 : 0];
        }

        CellStyle strike(boolean alt) {
            return strike[alt ? 1 : 0];
        }

        CellStyle status(String s, boolean alt) {
            return status.computeIfAbsent(s + alt, k -> {
                CellStyle c = base(alt, true);
                Font f = wb.createFont();
                f.setBold(true);
                String colour = switch (s) {
                    case "SUBMITTED" -> "17784A";
                    case "TERMINATED" -> "B42318";
                    case "IN_PROGRESS" -> "0129AC";
                    case "DETAILS_PENDING" -> "B4530F";
                    default -> "5B6B86";
                };
                ((org.apache.poi.xssf.usermodel.XSSFFont) f).setColor(new XSSFColor(hex(colour), null));
                c.setFont(f);
                return c;
            });
        }

        private CellStyle header(String colour) {
            CellStyle c = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            c.setFont(f);
            fill(c, colour);
            c.setAlignment(HorizontalAlignment.CENTER);
            c.setVerticalAlignment(VerticalAlignment.CENTER);
            c.setWrapText(true);
            border(c);
            return c;
        }

        private CellStyle base(boolean alt, boolean center) {
            CellStyle c = wb.createCellStyle();
            if (alt) fill(c, "F4F7FE");
            border(c);
            c.setVerticalAlignment(VerticalAlignment.CENTER);
            if (center) c.setAlignment(HorizontalAlignment.CENTER);
            return c;
        }

        private static void border(CellStyle c) {
            c.setBorderBottom(BorderStyle.THIN);
            c.setBorderTop(BorderStyle.THIN);
            c.setBorderLeft(BorderStyle.THIN);
            c.setBorderRight(BorderStyle.THIN);
            short grey = IndexedColors.GREY_25_PERCENT.getIndex();
            c.setBottomBorderColor(grey);
            c.setTopBorderColor(grey);
            c.setLeftBorderColor(grey);
            c.setRightBorderColor(grey);
        }

        private static void fill(CellStyle c, String colour) {
            ((XSSFCellStyle) c).setFillForegroundColor(new XSSFColor(hex(colour), null));
            c.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private static byte[] hex(String h) {
            return new byte[]{(byte) Integer.parseInt(h.substring(0, 2), 16),
                    (byte) Integer.parseInt(h.substring(2, 4), 16), (byte) Integer.parseInt(h.substring(4, 6), 16)};
        }
    }
}
