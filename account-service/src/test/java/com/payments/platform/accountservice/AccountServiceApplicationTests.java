package com.payments.platform.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceApplicationTests {

    @Test
    void contextLoads() {
        // Spring will now just connect to your running docker-compose database
    }

}