package io.micronaut.data.hibernate6;

import io.micronaut.data.tck.entities.Company;
import jakarta.inject.Singleton;
import org.hibernate.SessionFactory;

import javax.transaction.Transactional;

@Singleton
public class CompanyRepoService {

    private final CompanyRepo companyRepo;
    private final AuditCompanyRepository auditCompanyRepository;
    private final SessionFactory sessionFactory;

    public CompanyRepoService(CompanyRepo companyRepo, AuditCompanyRepository auditCompanyRepository, SessionFactory sessionFactory) {
        this.companyRepo = companyRepo;
        this.auditCompanyRepository = auditCompanyRepository;
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    void saveCompany(Company company) {
        companyRepo.save(company);
        company.getDateCreated();
        company.getLastUpdated();
    }

}
