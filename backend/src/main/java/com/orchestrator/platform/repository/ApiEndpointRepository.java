package com.orchestrator.platform.repository;

import com.orchestrator.platform.domain.entity.ApiEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpointEntity, UUID> {

    List<ApiEndpointEntity> findByServiceName(String serviceName);

    Optional<ApiEndpointEntity> findByServiceNameAndEndpointPathAndHttpMethod(
            String serviceName, String endpointPath, String httpMethod);

    @Query("SELECT DISTINCT a.serviceName FROM ApiEndpointEntity a ORDER BY a.serviceName")
    List<String> findDistinctServiceNames();

    @Query("SELECT a FROM ApiEndpointEntity a WHERE " +
           "(:serviceName IS NULL OR a.serviceName = :serviceName) AND " +
           "(:search IS NULL OR LOWER(a.endpointPath) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(a.operationId) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<ApiEndpointEntity> searchEndpoints(
            @Param("serviceName") String serviceName,
            @Param("search") String search);
}
