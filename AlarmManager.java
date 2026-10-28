package src;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AlarmManager {

    public void addAlarm(int userId, String timeStr, int playlistId) {
        try {
            LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME);
            String query = "INSERT INTO alarms (user_id, alarm_time, playlist_id, is_active) VALUES (?, ?, ?, true)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, timeStr);
                pstmt.setInt(3, playlistId);
                pstmt.executeUpdate();
                System.out.println("Alarm successfully set for " + timeStr);
            }
        } catch (DateTimeParseException e) {
            System.err.println("Invalid time format! Please use HH:mm:ss.");
        } catch (SQLException e) {
            System.err.println("Error saving alarm to database: " + e.getMessage());
        }
    }

    public void startAlarmChecker() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable alarmTask = () -> {
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:00"));
            String query = "SELECT a.alarm_id, p.file_path FROM alarms a JOIN playlists p ON a.playlist_id = p.playlist_id WHERE a.alarm_time = ? AND a.is_active = true";

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, currentTime);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    int alarmId = rs.getInt("alarm_id");
                    String filePath = rs.getString("file_path");

                    System.out.println("\n>>> ALARM RINGING! Time: " + currentTime + " <<<");
                    playMusic(filePath);
                    logAlarmTrigger(alarmId);
                }
            } catch (SQLException e) {
                System.err.println("Error checking alarms: " + e.getMessage());
            }
        };
        scheduler.scheduleAtFixedRate(alarmTask, 0, 1, TimeUnit.SECONDS);
    }

    private void playMusic(String filePath) {
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            System.err.println("Audio file not found at path: " + filePath);
            return;
        }

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
            System.out.println("Playing customized alarm track: " + audioFile.getName());
            Thread.sleep(10000); 
            clip.stop();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
            System.err.println("Error playing audio: " + e.getMessage());
        }
    }

    private void logAlarmTrigger(int alarmId) {
        String query = "INSERT INTO alarm_logs (alarm_id, status) VALUES (?, 'TRIGGERED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, alarmId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging alarm trigger: " + e.getMessage());
        }
    }
}