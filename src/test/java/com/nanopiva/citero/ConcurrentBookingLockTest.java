package com.nanopiva.citero;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.nanopiva.citero.support.IntegrationTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentBookingLockTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void secondLockWaitsForFirstTransactionToCommit() throws Exception {
        User owner = userRepository.save(User.builder().email("owner-conc@test.com").password("x").build());
        Business business = businessRepository.save(Business.builder().owner(owner).name("Conc").slug("conc").build());
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        Long staffId = staff.getId();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread t1 = new Thread(() -> tx.executeWithoutResult(status -> {
            staffRepository.findByIdForUpdate(staffId);
            firstLocked.countDown();
            try {
                releaseFirst.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        t1.start();

        assertTrue(firstLocked.await(3, TimeUnit.SECONDS), "T1 no obtuvo el lock");

        Thread t2 = new Thread(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    staffRepository.findByIdForUpdate(staffId);
                    secondAcquired.set(true);
                });
            } catch (Throwable e) {
                secondError.set(e);
            }
        });
        t2.start();

        Thread.sleep(400);
        assertFalse(secondAcquired.get(), "T2 no debería adquirir el lock mientras T1 lo mantiene");

        releaseFirst.countDown();
        t2.join(5000);
        t1.join(5000);

        if (secondError.get() != null) {
            secondError.get().printStackTrace();
        }
        assertTrue(secondAcquired.get(), "T2 debería adquirir el lock tras el commit de T1");
    }
}
