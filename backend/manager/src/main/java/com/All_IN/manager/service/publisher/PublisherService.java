package com.All_IN.manager.service.publisher;

import com.All_IN.manager.domain.publisher.Publisher;
import com.All_IN.manager.domain.publisher.PublisherPassword;
import com.All_IN.manager.domain.publisher.PublisherPasswordRepository;
import com.All_IN.manager.domain.publisher.PublisherRepository;
import com.All_IN.manager.service.publisher.exception.PublisherServiceException;
import com.All_IN.manager.service.publisher.exception.PublisherServiceValidateException;
import com.All_IN.manager.utils.Md5;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublisherService {

    private final PublisherRepository repository;
    private final PublisherPasswordRepository publisherPasswordRepository;

    private final PublisherValidateService validateService;

    private final Md5 md5;

    @Transactional
    public void save(long memberId) {
        Optional<Publisher> byMemberId = repository.findByMemberId(memberId);
        if (byMemberId.isPresent()) {
            throw new PublisherServiceValidateException(PublisherServiceException.EXIST_PUBLISHER);
        }

        String key = md5.encode(UUID.randomUUID().toString());

        repository.save(new Publisher(memberId, key));
    }

    public String getKey(Long publisherId) {
        Publisher publisher = validateService.validatePublisher(publisherId, PublisherValidateIdType.PUBLISHER);

        return publisher.getKey();
    }

    @Transactional
    public String generatePassword(Long publisherId) {
        Publisher publisher = validateService.validatePublisher(publisherId, PublisherValidateIdType.PUBLISHER);

        Optional<PublisherPassword> passwordByPublisher = publisherPasswordRepository.findByPublisher(publisher);
        if (passwordByPublisher.isPresent()) {
            throw new PublisherServiceValidateException(
                PublisherServiceException.ALREADY_GENERATE_PASSWORD);
        }

        PublisherPassword publisherPassword = new PublisherPassword(publisher);
        publisherPasswordRepository.save(publisherPassword);

        return publisherPassword.getValue();
    }

    @Transactional
    public void resetPassword(Long publisherId) {
        Publisher publisher = validateService.validatePublisher(publisherId, PublisherValidateIdType.PUBLISHER);

        PublisherPassword publisherPassword = publisherPasswordRepository.findByPublisher(publisher)
            .orElseThrow(() -> new PublisherServiceValidateException(PublisherServiceException.NO_PASSWORD));

        publisherPassword.use();

    }

    public String generateURL(Long publisherId) {
        Publisher publisher = validateService.validatePublisher(publisherId, PublisherValidateIdType.PUBLISHER);

        publisherPasswordRepository.findByPublisher(publisher)
            .orElseThrow(() -> new PublisherServiceValidateException(PublisherServiceException.NO_PASSWORD));

        return publisher.getKey() + "?pw=";
    }

    @Transactional
    public void usePassword(String password) {
        PublisherPassword publisherPassword = publisherPasswordRepository.findByValue(password)
            .orElseThrow(
                () -> new PublisherServiceValidateException(
                    PublisherServiceException.NO_MATCH_PASSWORD));

        publisherPassword.use();
    }

    @Transactional
    public void updateKey(Long publisherId) {
        Publisher publisher = validateService.validatePublisher(publisherId, PublisherValidateIdType.PUBLISHER);

        String key = md5.encode(UUID.randomUUID().toString());

        publisher.updateKey(key);
    }

}
