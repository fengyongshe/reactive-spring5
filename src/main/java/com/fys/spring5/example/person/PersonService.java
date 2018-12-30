package com.fys.spring5.example.person;

import com.fys.spring5.example.ip.IpService;
import com.mongodb.util.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PersonService {
    private final Logger log = LoggerFactory.getLogger(PersonRouter.class);

    private final IpService ipService;

    private final PersonRepository repository;

    public PersonService(IpService ipService, PersonRepository repository) {
        this.ipService = ipService;
        this.repository = repository;
    }

    public Mono<Person> addPerson(Mono<Person> mono) {
        return mono.flatMap(this::addIpInfo)
                .flatMap(repository::save);
    }

    private Mono<Person> addIpInfo(Person person) {
        return ipService.getIpInfo(person.getIp()).map(ipInfo -> person.copyWithIpInfo(JSON.parse(ipInfo)));
    }

    public void updateIpInfo() {
        LocalDateTime dateTime = LocalDateTime.now().minusDays(90);
        repository.findByUpdatedAtLessThan(dateTime)
                .buffer(300)
                .onBackpressureBuffer(5000)
                .parallel(2)
                .flatMap(this::updateIpInfo)
                .subscribe(
                        p -> log.info("Updated IP information for person with id {}", p.getId()),
                        t -> log.error("Failed IP information update stream", t)
                );
    }

    private Flux<Person> updateIpInfo(List<Person> batch) {
        return batch.stream()
                .map(this::addIpInfo)
                .map(monoP -> monoP.flux())
                .reduce(Flux.empty(), (p1, p2) -> p1.concatWith(p2))
                .flatMap(repository::save);
    }
}
