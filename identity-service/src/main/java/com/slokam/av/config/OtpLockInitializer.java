package com.slokam.av.config;
import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.transaction.annotation.Transactional;
import com.slokam.av.repository.AuthLockRepository;
import com.slokam.av.entity.AuthLock;
@Component
public class OtpLockInitializer implements ApplicationRunner {
 private final AuthLockRepository locks;
 public OtpLockInitializer(AuthLockRepository locks){this.locks=locks;}
 @Transactional public void run(ApplicationArguments args){for(int i=0;i<64;i++){if(!locks.existsById(i)){var lock=new AuthLock();lock.id=i;locks.save(lock);}}}
}
