package com.villaging.ai.script;

import java.util.List;

/** A parsed, validated task script. */
public record Script(List<Step> steps) {
}
