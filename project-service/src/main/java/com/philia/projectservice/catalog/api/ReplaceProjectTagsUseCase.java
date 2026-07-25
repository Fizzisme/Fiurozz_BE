package com.philia.projectservice.catalog.api;

/** Application entry point for replacing a project's complete tag collection. */
public interface ReplaceProjectTagsUseCase {

    ReplaceProjectTagsResult replaceProjectTags(ReplaceProjectTagsCommand command);
}
