package com.jjanpot.server.domain.notification_template.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jjanpot.server.domain.notification.repository.NotificationRepositoryCustom;
import com.jjanpot.server.domain.notification_template.entity.NotificationSubTemplateType;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplate;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;

public interface NotificationTemplateRepository
	extends JpaRepository<NotificationTemplate, Long>, NotificationRepositoryCustom {

	@Query("""
			SELECT template
			FROM NotificationTemplate template
			WHERE template.type = :type
				AND template.subType = :subType
		""")
	List<NotificationTemplate> findTemplateByType(
		@Param("type") NotificationTemplateType type,
		@Param("subType") NotificationSubTemplateType subType
	);

	List<NotificationTemplate> findByType(NotificationTemplateType type);
}
