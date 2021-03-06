package unicon.matthews.oneroster.service;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import unicon.matthews.oneroster.Class;
import unicon.matthews.oneroster.Enrollment;
import unicon.matthews.oneroster.Status;
import unicon.matthews.oneroster.User;
import unicon.matthews.oneroster.exception.EnrollmentNotFoundException;
import unicon.matthews.oneroster.service.repository.MongoEnrollment;
import unicon.matthews.oneroster.service.repository.MongoEnrollmentRepository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * @author ggilbert
 * @author xchopin <xavier.chopin@univ-lorraine.fr>
 *
 */
@Service
public class EnrollmentService {
  
  private MongoEnrollmentRepository mongoEnrollmentRepository;
  private final MongoOperations mongoOps;
  
  @Autowired
  public EnrollmentService(MongoEnrollmentRepository mongoEnrollmentRepository, MongoOperations mongoOperations) {
    this.mongoEnrollmentRepository = mongoEnrollmentRepository;
    this.mongoOps = mongoOperations;
  }

  public Enrollment save(final String tenantId, final String orgId, final String classId, Enrollment enrollment, boolean check) {
    
    if (StringUtils.isBlank(tenantId) 
        || StringUtils.isBlank(orgId)
        || enrollment == null
        || enrollment.getUser() == null
        || StringUtils.isBlank(enrollment.getUser().getSourcedId())) {
      throw new IllegalArgumentException();
    }
    
    unicon.matthews.oneroster.Class klassLink = new Class.Builder().withSourcedId(classId).build();
    
    User userLink = new User.Builder().withSourcedId(enrollment.getUser().getSourcedId()).build();
    
    Enrollment enrollmentWithLinks = new Enrollment.Builder()
          .withKlass(klassLink)
          .withMetadata(enrollment.getMetadata())
          .withPrimary(enrollment.isPrimary())
          .withRole(enrollment.getRole())
          .withSourcedId(enrollment.getSourcedId())
          .withStatus(enrollment.getStatus())
          .withUser(userLink)
          .build();


    MongoEnrollment mongoEnrollment = null;

    if (check)
      mongoEnrollment = mongoEnrollmentRepository.findByTenantIdAndOrgIdAndClassSourcedIdAndUserSourcedIdIgnoreCase(tenantId, orgId, enrollmentWithLinks.getKlass().getSourcedId(), enrollmentWithLinks.getUser().getSourcedId());


    if (mongoEnrollment == null) {
      mongoEnrollment = convert(
              tenantId,
              orgId,
              enrollmentWithLinks.getKlass().getSourcedId(),
              enrollmentWithLinks.getUser().getSourcedId(),
              enrollmentWithLinks
      );
    } else {
      mongoEnrollment
        = new MongoEnrollment.Builder()
          .withId(mongoEnrollment.getId())
          .withClassSourcedId(mongoEnrollment.getClassSourcedId())
          .withEnrollment(enrollmentWithLinks)
          .withOrgId(mongoEnrollment.getOrgId())
          .withTenantId(mongoEnrollment.getTenantId())
          .withUserSourcedId(mongoEnrollment.getUserSourcedId())
          .build();
    }
    
    MongoEnrollment savedMongoEnrollment = mongoEnrollmentRepository.save(mongoEnrollment);
    
    return savedMongoEnrollment.getEnrollment(); 
  }
  
  public Collection<Enrollment> findEnrollmentsForClass(final String tenantId, final String orgId, 
      final String classSourcedId) throws EnrollmentNotFoundException {
    Collection<MongoEnrollment> mongoEnrollments 
      = mongoEnrollmentRepository.findByTenantIdAndOrgIdAndClassSourcedIdAndEnrollmentStatus(tenantId, orgId, classSourcedId, Status.active);
    
    if (mongoEnrollments != null && !mongoEnrollments.isEmpty()) {
      return mongoEnrollments.stream().map(MongoEnrollment::getEnrollment).collect(Collectors.toList());
    }
    throw new EnrollmentNotFoundException("Enrollment not found.");
  }
  
  public Collection<Enrollment> findEnrollmentsForUser(final String tenantId, final String orgId, final String userSourcedId) throws EnrollmentNotFoundException {

    Collection<MongoEnrollment> mongoEnrollments;
    Query query = new Query();

    query.addCriteria(where("userSourcedId").is(userSourcedId).and("orgId").is(orgId).and("tenantId").is(tenantId));

    mongoEnrollments= mongoOps.find(query, MongoEnrollment.class);

    if (mongoEnrollments != null && !mongoEnrollments.isEmpty())
      return mongoEnrollments.stream().map(MongoEnrollment::getEnrollment).collect(Collectors.toList());

    throw new EnrollmentNotFoundException("Enrollment not found.");
  }
  
  private MongoEnrollment convert(final String tenantId, final String orgId, 
      final String classSourcedId, final String userSourcedId, Enrollment enrollment) {
    MongoEnrollment mongoEnrollment
      = new MongoEnrollment.Builder()
        .withClassSourcedId(classSourcedId)
        .withEnrollment(enrollment)
        .withOrgId(orgId)
        .withTenantId(tenantId)
        .withUserSourcedId(userSourcedId)
        .build();
    
    return mongoEnrollment;
  }
  
}
