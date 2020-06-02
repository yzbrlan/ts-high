package travel.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import travel.entity.Trip;
import travel.entity.TripId;

@Service
@CacheConfig(cacheNames = "trip")
public class TripService {
    @Autowired
    private TripRepository repository;

    @Cacheable(key = "#id")
    public Trip findByTripId(String id){
        return repository.findByTripId(new TripId(id));
    }

    @CacheEvict(key = "#tripId.id")
    public void deleteByTripId(TripId tripId){
        repository.deleteByTripId(tripId);
    }
}
