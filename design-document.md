# Scalable Social Feed System - Design Document

## Executive Summary

This document presents a scalable architecture for a social media feed system that efficiently handles millions of users and posts. The design specifically addresses performance bottlenecks caused by high-follower accounts through an innovative hybrid approach, ensuring consistent sub-second response times regardless of user follower counts.

## Problem Statement

Traditional social feed systems face critical scalability challenges when users with millions of followers create posts. A single post from such users can trigger millions of database writes, causing:
- System-wide performance degradation
- Increased latency for all users
- Database overload and potential failures
- Poor user experience during peak usage

## Solution Overview

Our hybrid architecture dynamically routes content based on user characteristics:
- **Regular users** (< 5,000 followers): Utilize pre-computed feeds for optimal read performance
- **High-follower users** (≥ 5,000 followers): Employ on-demand content retrieval to prevent write amplification

This approach maintains consistent performance while supporting unlimited scale.

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Load Balancer                               │
└─────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                       │
┌───────▼────────┐    ┌──────────────┐    ┌──────────────┐   │
│  API Gateway   │    │ API Gateway  │    │ API Gateway  │   │
└───────┬────────┘    └──────┬───────┘    └──────┬───────┘   │
        │                     │                    │           │
        └─────────────────────┴────────────────────┘           │
                              │                                │
                    ┌─────────▼──────────┐                    │
                    │   API Servers      │◄───────────────────┘
                    │   (Stateless)      │
                    └─────────┬──────────┘
                              │
        ┌─────────────────────┼─────────────────────────┐
        │                     │                         │
┌───────▼────────┐    ┌──────▼───────┐    ┌───────────▼────────┐
│  Post Service  │    │ Feed Service │    │  User Service      │
└───────┬────────┘    └──────┬───────┘    └───────────┬────────┘
        │                     │                         │
        │              ┌──────▼───────┐                │
        │              │Message Queue │                │
        │              │  (Kafka)     │                │
        │              └──────┬───────┘                │
        │                     │                         │
        │              ┌──────▼───────┐                │
        │              │ Fan-out      │                │
        │              │ Service      │                │
        │              └──────┬───────┘                │
        │                     │                         │
┌───────▼────────────────────▼────────────────────────▼────────┐
│                      Data Layer                               │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐             │
│  │  Post DB   │  │  Feed DB   │  │  User DB   │             │
│  │ (Cassandra)│  │ (Cassandra)│  │ (PostgreSQL│             │
│  └────────────┘  └────────────┘  └────────────┘             │
│                                                               │
│  ┌─────────────────────────────────────────────┐            │
│  │            Redis Cache Layer                 │            │
│  └─────────────────────────────────────────────┘            │
└───────────────────────────────────────────────────────────────┘
```

### Data Model

#### Entity Relationship Diagram

```
┌─────────────────────┐          ┌─────────────────────┐
│       Users         │          │       Posts         │
├─────────────────────┤          ├─────────────────────┤
│ user_id (PK)       │          │ post_id (PK)       │
│ username           │          │ author_id (FK)     │
│ email              │          │ content            │
│ follower_count     │          │ media_urls[]       │
│ is_celebrity       │          │ created_at         │
│ created_at         │          │ likes_count        │
│ profile_image_url  │          │ comments_count     │
└─────────────────────┘          └─────────────────────┘
         │                                │
         │ 1                              │ 1
         │                                │
         │ M                              │ M
         ▼                                ▼
┌─────────────────────┐          ┌─────────────────────┐
│      Follows        │          │       Feeds         │
├─────────────────────┤          ├─────────────────────┤
│ follower_id (FK)   │          │ user_id (FK)       │
│ following_id (FK)  │          │ post_id (FK)       │
│ created_at         │          │ created_at         │
│ (follower_id,      │          │ (user_id,          │
│  following_id) PK  │          │  created_at) PK    │
└─────────────────────┘          └─────────────────────┘
```

### Component Details

#### 1. API Gateway
- Request routing and load balancing
- Authentication and rate limiting
- Request validation and transformation

#### 2. API Servers
- Stateless application logic
- User type detection and routing
- Feed assembly and sorting

#### 3. Post Service
- Post creation and storage
- Media handling and validation
- Post retrieval for celebrity users

#### 4. Feed Service
- Pre-computed feed management
- Feed retrieval and caching
- Hybrid feed assembly

#### 5. User Service
- User profile management
- Follower count tracking
- Celebrity status determination

#### 6. Message Queue (Kafka)
- Asynchronous task distribution
- Fan-out job queuing
- System decoupling

#### 7. Fan-out Service
- Distributed feed updates
- Batch processing optimization
- Regular user feed population

## Data Flow Diagrams

### Write Path - Regular User

```
User Device          API Gateway         API Server         Post Service
    │                    │                   │                  │
    │  Create Post       │                   │                  │
    ├───────────────────>│                   │                  │
    │                    │   Forward          │                  │
    │                    ├──────────────────>│                  │
    │                    │                   │  Check User Type │
    │                    │                   ├─────────┐        │
    │                    │                   │         │        │
    │                    │                   │<────────┘        │
    │                    │                   │  Save Post       │
    │                    │                   ├─────────────────>│
    │                    │                   │                  │
    │                    │                   │                  ├──> Post DB
    │                    │                   │                  │
    │                    │                   │  Publish to Queue│
    │                    │                   ├─────────────────>│
    │                    │                   │                  │
    │                    │                   │                  ├──> Kafka
    │                    │                   │                  │
    │  Success Response  │                   │                  │
    │<───────────────────┼───────────────────┤                  │
    │                    │                   │                  │
    
                            Asynchronous Fan-out Process
                                        │
                                        ▼
                                 ┌─────────────┐
                                 │  Fan-out    │
                                 │  Service    │
                                 └──────┬──────┘
                                        │
                                        ├──> Get Followers
                                        │
                                        ├──> Batch Updates
                                        │
                                        └──> Update Feed DB
```

### Write Path - Celebrity User

```
User Device          API Gateway         API Server         Post Service
    │                    │                   │                  │
    │  Create Post       │                   │                  │
    ├───────────────────>│                   │                  │
    │                    │   Forward          │                  │
    │                    ├──────────────────>│                  │
    │                    │                   │  Check User Type │
    │                    │                   ├─────────┐        │
    │                    │                   │         │        │
    │                    │                   │<────────┘        │
    │                    │                   │  (Celebrity)     │
    │                    │                   │  Save Post Only  │
    │                    │                   ├─────────────────>│
    │                    │                   │                  │
    │                    │                   │                  ├──> Post DB
    │                    │                   │                  │
    │  Success Response  │                   │                  │
    │<───────────────────┼───────────────────┤                  │
    │                    │                   │                  │
    
    Note: No fan-out process triggered for celebrity posts
```

### Read Path - Hybrid Feed Assembly

```
User Device          API Gateway         API Server         Services
    │                    │                   │                  │
    │  Request Feed      │                   │                  │
    ├───────────────────>│                   │                  │
    │                    │   Forward          │                  │
    │                    ├──────────────────>│                  │
    │                    │                   │                  │
    │                    │                   ├── Get Base Feed ─┼──> Cache/Feed DB
    │                    │                   │                  │
    │                    │                   ├── Get Celebrity ─┼──> Post DB
    │                    │                   │     Posts        │
    │                    │                   │                  │
    │                    │                   │  Merge & Sort    │
    │                    │                   ├─────────┐        │
    │                    │                   │         │        │
    │                    │                   │<────────┘        │
    │                    │                   │                  │
    │  Feed Response     │                   │                  │
    │<───────────────────┼───────────────────┤                  │
    │                    │                   │                  │
```

## Performance Characteristics

### Write Performance
- **Regular Users**: O(1) write + async O(N) fan-out (N = follower count)
- **Celebrity Users**: O(1) write only
- **Write Latency**: < 50ms for all users

### Read Performance
- **Base Feed Retrieval**: O(1) from cache/pre-computed feed
- **Celebrity Post Retrieval**: O(M) where M = number of followed celebrities
- **Read Latency**: < 100ms for 99th percentile

### Scalability Metrics
- Supports 100M+ active users
- Handles 1M+ posts per minute
- Maintains < 100ms feed load time
- Zero performance degradation with celebrity posts

## Implementation Considerations

### Database Selection
- **User Data**: PostgreSQL for ACID compliance and complex queries
- **Posts & Feeds**: Cassandra for horizontal scalability and write throughput
- **Cache Layer**: Redis for sub-millisecond response times

### Optimization Strategies
1. **Intelligent Caching**: Cache celebrity posts with higher TTL
2. **Batch Processing**: Group fan-out operations for efficiency
3. **Connection Pooling**: Minimize database connection overhead
4. **CDN Integration**: Serve media content from edge locations

### Monitoring & Observability
- Real-time performance metrics dashboard
- Automated alerting for threshold breaches
- Distributed tracing for request flow analysis
- A/B testing framework for threshold optimization

## Conclusion

This hybrid architecture successfully addresses the scalability challenges of modern social media platforms. By intelligently routing content based on user characteristics, the system maintains consistent performance while supporting unlimited growth. The design ensures that all users, regardless of their follower count, experience fast and reliable service.