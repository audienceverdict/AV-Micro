package com.slokam.social.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ResponseStatus;

@Configuration
public class OpenApiConfiguration {
  @Bean
  public OpenAPI socialOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Social Media Operator API").version("1.0.0")
            .description("Identity JWT authenticated administrator API for videos, comments, statistics, publishing and replies. "
                + "Comment operations additionally require account allowlists keyed by Identity token subject."))
        .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("identityBearer"))
        .components(schemas().addSecuritySchemes("identityBearer", new SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));

  }

  @Bean
  public OperationCustomizer operatorResponses() {
    return (operation, handler) -> {
      if (!handler.getBeanType().getPackageName().startsWith("com.slokam.social")) return operation;
      var status = handler.getMethodAnnotation(ResponseStatus.class);
      String code = status == null ? "200" : Integer.toString(status.value().value());
      var success = operation.getResponses().get(code);
      if (success != null) success.setDescription(switch (code) {
        case "201" -> "Created; returns the saved resource.";
        case "202" -> "Accepted into the durable queue; delivery is asynchronous.";
        case "204" -> "Completed; no response body.";
        default -> "Successful request; returns stored data or queue state.";
      });
      operation.getResponses().addApiResponse("400", new ApiResponse().description("Invalid input, missing required header/part, or unsupported operation parameters."));
      if (handler.getMethod().getName().equals("media")) operation.setSecurity(java.util.List.of());
      String name = handler.getMethod().getName();
      String controller = handler.getBeanType().getSimpleName();
      if (handler.getMethodParameters().length > 0 && !name.equals("list") && !name.equals("inbox") && !name.equals("unassigned") && !name.equals("create"))
        operation.getResponses().addApiResponse("404", new ApiResponse().description("Requested local resource was not found."));
      if (controller.equals("ReplyController")) {
        operation.getResponses().addApiResponse("401", new ApiResponse().description("Missing or invalid Identity access token."));
        operation.getResponses().addApiResponse("403", new ApiResponse().description("Operator is not allowed to access this account."));
      }
      if (controller.equals("ReplyController") && name.equals("reply"))
        operation.getResponses().addApiResponse("422", new ApiResponse().description("Stored platform does not support outbound replies."));
      if (name.equals("assign") || name.equals("reset") || name.equals("retry") || name.equals("reply") || name.equals("confirm"))
        operation.getResponses().addApiResponse("409", new ApiResponse().description("Conflicting assignment, idempotency key, active lease, cooldown or job state; see operation description."));
      if (controller.equals("PublishingController") && (name.equals("create") || name.equals("publish"))) {
        operation.getResponses().addApiResponse("503", new ApiResponse().description("Publishing is disabled; save with draft=true."));
        if (name.equals("create")) {
          operation.getResponses().addApiResponse("413", new ApiResponse().description("Media or multipart request exceeds configured upload limits."));
          operation.getResponses().addApiResponse("415", new ApiResponse().description("Expected multipart/form-data with an application/json metadata part."));
        }
      }
      if (controller.equals("PublishingController") && name.equals("media") && success != null) {
        var binary = new Content();
        for (String mime : java.util.List.of("image/jpeg", "image/png", "video/mp4"))
          binary.addMediaType(mime, new MediaType().schema(new StringSchema().format("binary")));
        success.setContent(binary);
        operation.getResponses().addApiResponse("206", new ApiResponse().description("Requested byte range.").content(binary));
        operation.getResponses().addApiResponse("416", new ApiResponse().description("Requested byte range cannot be satisfied."));
      }
      String model = switch (controller) {
        case "VideoController" -> switch (name) {
          case "create", "list", "get" -> "ApplicationVideo";
          case "uploads", "unassigned" -> "Upload";
          case "comments" -> "Comment";
          case "status" -> "CommentSyncTask";
          case "audit" -> "AssignmentAudit";
          default -> null;
        };
        case "StatisticsController" -> name.equals("accounts") ? "Account" : name.endsWith("History") ? "MetricSnapshot" : "MetricTask";
        case "PublishingController" -> name.equals("media") || name.equals("retry") ? null : "SavedContent";
        case "ReplyController" -> switch (name) {
          case "inbox" -> "Comment";
          case "thread" -> "CommentThread";
          case "reply", "get" -> "ReplyJob";
          default -> null;
        };
        default -> null;
      };
      if (success != null && model != null) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/" + model);
        if (java.util.List.class.isAssignableFrom(handler.getMethod().getReturnType()))
          schema = new ArraySchema().items(schema);
        success.setContent(new Content().addMediaType("application/json", new MediaType().schema(schema)));
      }
      return operation;
    };
  }

  private static Components schemas() {
    var components = new Components();
    components.addSchemas("ApplicationVideo", row("id title description created_at updated_at"));
    components.addSchemas("Upload", row("id platform account_id external_content_id title permalink engagement_metadata retrieved_at"));
    components.addSchemas("Comment", row("id content_id external_comment_id parent_external_id author_id author_name comment_text published_at provider_updated_at retrieved_at like_count reply_count resolved_at resolved_by platform account_id external_content_id permalink external_account_id account_name"));
    components.addSchemas("CommentSyncTask", row("id content_id parent_external_id page_cursor due_at last_success_at status error_message pages_fetched"));
    components.addSchemas("AssignmentAudit", row("id content_id old_video_id new_video_id changed_at"));
    components.addSchemas("Account", row("id platform external_account_id account_name enabled"));
    components.addSchemas("MetricTask", row("task_id account_id content_id platform operation enabled status last_attempt_at last_success_at due_at error_message result"));
    components.addSchemas("MetricSnapshot", row("id task_id account_id content_id platform operation snapshot_bucket observed_at window_from window_to result"));
    components.addSchemas("Publication", row("id platform external_account_id status external_content_id permalink error_message attempts updated_at"));
    components.addSchemas("SavedContent", row("id title caption content_type media_type media_size youtube_privacy created_at"));
    components.addSchemas("ReplyJob", row("id comment_id request_key reply_text actor status external_reply_id reconciled_by error_message attempts next_attempt_at started_at created_at updated_at"));
    components.getSchemas().get("SavedContent").addProperty("publications", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Publication")));
    components.addSchemas("CommentThread", new ObjectSchema()
        .addProperty("comment", new Schema<>().$ref("#/components/schemas/Comment"))
        .addProperty("comments", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Comment")))
        .addProperty("replies", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ReplyJob"))));
    ((Schema<?>) components.getSchemas().get("ApplicationVideo").getProperties().get("id")).setExample("VID-00000000-0000-0000-0000-000000000001");
    for (String name : java.util.List.of("Upload", "Comment", "CommentSyncTask", "AssignmentAudit", "Account", "MetricSnapshot"))
      components.getSchemas().get(name).addProperty("id", new IntegerSchema().format("int64"));
    return components;
  }

  // JDBC rows retain snake_case names. Provider JSON remains deliberately open-ended.
  private static ObjectSchema row(String fields) {
    var schema = new ObjectSchema();
    for (String field : fields.split(" ")) {
      Schema<?> property;
      if (field.equals("enabled")) property = new BooleanSchema();
      else if (java.util.Set.of("content_id", "account_id", "task_id", "comment_id", "like_count", "reply_count", "pages_fetched", "attempts", "media_size").contains(field))
        property = new IntegerSchema().format("int64");
      else if (field.equals("result")) property = new ObjectSchema().additionalProperties(true)
          .description("Provider-specific structured JSON. Preserve metric names, dimensions and reporting periods; absent metrics are not zero.");
      else if (field.equals("engagement_metadata")) property = new StringSchema().nullable(true)
          .description("Stored engagement JSON returned as a database string, when available.");
      else property = new StringSchema();
      if (field.endsWith("_at") || field.equals("window_from") || field.equals("window_to"))
        property.setDescription("Database UTC timestamp, when available.");
      schema.addProperty(field, property);
    }
    return schema;
  }
}
