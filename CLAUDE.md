# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains the architecture specification for a scalable social feed system that solves the "celebrity problem" using a hybrid approach. The system intelligently routes posts through different pipelines based on user follower counts to prevent system bottlenecks.

## Key Architecture Concepts

### The Celebrity Problem
Users with millions of followers can cause massive write spikes when posting, leading to:
- Database overload from simultaneous feed updates
- High latency in post propagation
- System-wide performance degradation

### Hybrid Solution
The system uses two distinct strategies based on follower count threshold (5,000):

1. **Normal Users (< 5,000 followers)**: Fan-out on write (push model)
   - Posts are immediately pushed to all followers' feeds
   - Optimized for real-time updates and low latency

2. **Celebrities (> 5,000 followers)**: Fan-out on read (pull model)
   - Posts are only saved to the posts table
   - Followers' feeds pull celebrity posts at read time
   - Prevents massive write operations

## Implementation Requirements

### Data Model
The user model must include:
- `follower_count` field or `is_celebrity` boolean flag
- This enables instant routing decisions at write time

### Write Path Implementation
- Check user type before processing posts
- Route to appropriate pipeline based on follower count
- Celebrity posts skip the fan-out service entirely

### Read Path Implementation
- Implement parallel fetching:
  1. Base feed from pre-computed feeds table (non-celebrity posts)
  2. Celebrity posts directly from posts table
- Merge and sort results by `created_at` timestamp
- Consider caching strategies for celebrity post queries

## Performance Considerations

- Celebrity post queries should be optimized with appropriate indexes on `author_id` and `created_at`
- Consider implementing pagination for celebrity post fetching
- Monitor the 5,000 follower threshold and adjust based on system performance
- Implement caching for frequently accessed celebrity posts

## Testing Focus Areas

When implementing or modifying this system:
- Test the user type detection logic thoroughly
- Verify correct routing for edge cases (users crossing the 5,000 threshold)
- Load test celebrity post creation to ensure no fan-out occurs
- Test feed assembly performance with varying numbers of followed celebrities
- Ensure proper sorting when merging feed sources