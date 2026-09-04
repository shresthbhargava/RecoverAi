package com.recoverai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class RecoveraiApplication {

    /*
     * Pin the JVM default zone to the canonical IANA name for IST.
     *
     * Why this exists: on Windows, Java maps "India Standard Time" to the deprecated alias
     * "Asia/Calcutta". The Postgres JDBC driver sends TimeZone.getDefault().getID() as a
     * connection startup parameter, and a server whose tzdata omits the backward-compatibility
     * aliases rejects it with
     *     FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"
     * which kills the connection before Flyway can run, so the app never starts.
     *
     * "Asia/Kolkata" is the same zone (+05:30, identical rules), so this is a rename and not a
     * behaviour change. Pinning it is also the more correct choice on its own merits: the
     * NO_CUSTOMER_CONTACT_AFTER policy means 9pm for an Indian customer, not 9pm wherever the
     * server happens to be running.
     *
     * Do NOT "fix" this instead by setting hibernate.jdbc.time_zone to UTC. Every timestamp
     * column is TIMESTAMP (no zone) and every entity field is an Instant, so that would shift
     * stored values by 5.5 hours, and PolicyEngine reads the local hour-of-day for the
     * no-contact rule, so it would misfire too.
     *
     * A static block rather than the first line of main(), because it then also covers entry
     * points that never call main() - a Spring Boot test context, for instance. It has to happen
     * before any connection is opened, since the driver reads the default zone at that moment.
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        SpringApplication.run(RecoveraiApplication.class, args);
    }
}
