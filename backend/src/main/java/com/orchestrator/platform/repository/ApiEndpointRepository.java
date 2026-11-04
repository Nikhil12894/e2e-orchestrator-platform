package com.orchestrator.platform.repository;

import com.orchestrator.platform.domain.entity.ApiEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpointEntity, UUID>, JpaSpecificationExecutor<ApiEndpointEntity> {

    List<ApiEndpointEntity> findByServiceName(String serviceName);

    Optional<ApiEndpointEntity> findByServiceNameAndEndpointPathAndHttpMethod(
            String serviceName, String endpointPath, String httpMethod);

    @Query("SELECT DISTINCT a.serviceName FROM ApiEndpointEntity a ORDER BY a.serviceName")
    List<String> findDistinctServiceNames();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ApiEndpointEntity a WHERE a.serviceName = :serviceName")
    int deleteByServiceName(@org.springframework.data.repository.query.Param("serviceName") String serviceName);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ApiEndpointEntity a")
    void deleteAllEndpoints();
}
