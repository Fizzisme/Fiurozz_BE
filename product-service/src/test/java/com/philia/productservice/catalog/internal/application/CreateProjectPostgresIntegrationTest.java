package com.philia.productservice.catalog.internal.application;

import com.philia.productservice.ProductServiceApplication;
import com.philia.productservice.catalog.api.CreateProjectCommand;
import com.philia.productservice.catalog.api.CreateProjectUseCase;
import com.philia.productservice.shared.security.GatewayHeaderAuthenticationFilter;
import com.philia.productservice.shared.security.GatewayActorPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ProductServiceApplication.class,
        properties = "spring.docker.compose.enabled=false"
)
@Transactional
class CreateProjectPostgresIntegrationTest {

    @Autowired
    private CreateProjectUseCase createProjectUseCase;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsProjectAndTagsInPostgres() {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);
        authenticate(ownerId);

        var result = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId,
                "Fiurozz Backend",
                "Fiurozz Backend " + suffix,
                "A project catalog backend",
                "The complete project description",
                "https://demo.example.com",
                "PRIVATE",
                List.of("Java", "Spring Boot"),
                List.of("Project Catalog"),
                List.of(tagId)
        ));

        var projectCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM projects
                        WHERE id = :projectId
                          AND owner_id = :ownerId
                          AND status = 'DRAFT'
                          AND visibility = 'PRIVATE'
                          AND source_visibility = 'HIDDEN'
                          AND tech_stack = '["java", "spring-boot"]'::jsonb
                        """)
                .param("projectId", result.id())
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
        var tagCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM project_tags
                        WHERE project_id = :projectId
                          AND tag_id = :tagId
                        """)
                .param("projectId", result.id())
                .param("tagId", tagId)
                .query(Long.class)
                .single();

        assertThat(projectCount).isEqualTo(1);
        assertThat(tagCount).isEqualTo(1);
        assertThat(result.tags()).singleElement().satisfies(tag -> assertThat(tag.id()).isEqualTo(tagId));
    }

    @Test
    void createsProjectThroughHttpApi() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);

        var requestBody = """
                {
                  "subCategoryId": "%s",
                  "title": "Fiurozz Backend",
                  "slug": "Fiurozz Backend %s",
                  "shortDescription": "A project catalog backend",
                  "description": "The complete project description",
                  "demoUrl": "https://demo.example.com",
                  "visibility": "PRIVATE",
                  "techStack": ["Java", "Spring Boot"],
                  "features": ["Project Catalog"],
                  "tagIds": ["%s"]
                }
                """.formatted(subCategoryId, suffix, tagId);

        var mvcResult = mockMvc().perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_CREATED"))
                .andExpect(jsonPath("$.data.owner.id").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.sourceVisibility").value("HIDDEN"))
                .andExpect(jsonPath("$.data.techStack[1]").value("spring-boot"))
                .andExpect(jsonPath("$.data.tags[0].id").value(tagId.toString()))
                .andReturn();

        var responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsByteArray());
        var projectId = UUID.fromString(responseJson.path("data").path("id").asText());
        var projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();

        assertThat(projectCount).isEqualTo(1);
    }

    @Test
    void wrapsMissingAuthenticationInApiResponse() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc().perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void publishesCreateProjectOpenApiDocumentation() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fiurozz Product Service API"))
                .andExpect(jsonPath("$.paths.length()").value(1))
                .andExpect(jsonPath("$['paths']['/api/v1/projects']['post']['operationId']")
                        .value("createProject"))
                .andExpect(jsonPath("$['paths']['/api/v1/projects']['post']['security'][0]['bearerAuth']")
                        .exists())
                .andExpect(jsonPath("$['paths']['/api/v1/projects']['post']['responses']['201']")
                        .exists())
                .andExpect(jsonPath("$['components']['securitySchemes']['bearerAuth']['scheme']")
                        .value("bearer"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    private void insertReferences(UUID categoryId, UUID subCategoryId, UUID tagId, String suffix) {
        jdbcClient.sql("""
                        INSERT INTO project_categories (id, key, slug, title)
                        VALUES (:id, :key, :slug, :title)
                        """)
                .param("id", categoryId)
                .param("key", "category-" + suffix)
                .param("slug", "category-" + suffix)
                .param("title", "Category " + suffix)
                .update();
        jdbcClient.sql("""
                        INSERT INTO project_sub_categories (id, category_id, key, slug, title)
                        VALUES (:id, :categoryId, :key, :slug, :title)
                        """)
                .param("id", subCategoryId)
                .param("categoryId", categoryId)
                .param("key", "subcategory-" + suffix)
                .param("slug", "subcategory-" + suffix)
                .param("title", "Subcategory " + suffix)
                .update();
        jdbcClient.sql("""
                        INSERT INTO tags (id, slug, display_name, normalized_name)
                        VALUES (:id, :slug, :displayName, :normalizedName)
                        """)
                .param("id", tagId)
                .param("slug", "tag-" + suffix)
                .param("displayName", "Tag " + suffix)
                .param("normalizedName", "tag-" + suffix)
                .update();
    }

    private static void authenticate(UUID ownerId) {
        var principal = new GatewayActorPrincipal(ownerId, "owner@example.com", "Project Owner", null);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
