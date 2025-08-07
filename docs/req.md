A Hybrid Approach for a Scalable Social Feed
This document outlines an advanced, hybrid architecture for a social feed system. It specifically addresses the "celebrity problem"—where users with millions of followers can cause system bottlenecks—by combining two distinct strategies: fan-out on write for regular users and fan-out on read for celebrities.

1. The Challenge: The "Celebrity Problem"

The standard "fan-out on write" model, where a user's post is immediately pushed into the feeds of all their friends, is highly efficient for the average user. However, when a user with millions of followers (a "celebrity") posts, this strategy leads to:

Massive Write Spikes: Attempting to write to millions of individual feeds simultaneously can overload the database and message queues.

High Latency: The celebrity's post takes a long time to propagate to all followers, defeating the real-time nature of a feed.

System Instability: The resources consumed by a single celebrity post can slow down the entire system for all other users.

To solve this, we introduce a hybrid model that treats regular users and celebrities differently.

2. The Hybrid Solution: Combining "Push" and "Pull"

The core of the hybrid solution is to dynamically choose a strategy based on the user's follower count.

For Normal Users (< 5,000 followers): We use the fan-out on write (push) model. When they post, the system proactively pushes their post to their friends' feeds. This is fast and efficient for the vast majority of users.

For Celebrities (> 5,000 followers): We use the fan-out on read (pull) model. When they post, the system does nothing to follower feeds. Instead, when a user requests their own feed, the system actively pulls in recent posts from any celebrities they follow and merges them at read time.

3. Updated System Design (Hybrid Model)

This diagram illustrates the decision-making logic introduced in the hybrid model.

graph TD
    subgraph "User's Device"
        Client
    end

    subgraph "Cloud Infrastructure"
        API_Gateway --> API_Servers
        
        subgraph "Write Path Logic"
            API_Servers -- "1. Create Post" --> CheckUserType
            CheckUserType{Is User a Celebrity?}
            CheckUserType -- "No" --> FanOutOnWrite["Fan-out on Write (Push)"]
            CheckUserType -- "Yes" --> WriteToPostDB["Write to Post DB Only"]

            FanOutOnWrite --> PostDB_Write["1. Write to Post DB"]
            FanOutOnWrite --> MessageQueue["2. Publish to Queue"]
            MessageQueue --> FanOutService["Fan-out Service"]
            FanOutService --> InjectToFeeds["Inject into Friends' Feeds"]
            InjectToFeeds --> FeedDB_Write(Feed DB)
            WriteToPostDB --> PostDB(Post DB)
            PostDB_Write --> PostDB
        end

        subgraph "Read Path Logic"
            API_Servers -- "a. Request Feed" --> MergeFeeds{Merge Feeds}
            MergeFeeds -- "b. Get Base Feed (Friends)" --> Cache[(Redis Cache)]
            Cache -- "Cache Miss" --> FeedDB_Read(Feed DB)
            MergeFeeds -- "c. Get Celebrity Posts (Pull)" --> PostDB_Read(Post DB)
            MergeFeeds -- "d. Merge & Sort" --> FinalFeed
            FinalFeed --> Client
        end
    end



4. Detailed Hybrid Workflows

Data Model Prerequisite

To enable this logic, the users data model must contain a field to identify celebrities, such as follower_count or a boolean is_celebrity. This allows the API Server to make an instant decision.

Write Path (Hybrid)

A user sends a POST request to create a post.

The API Server receives the request and checks the user's type (e.g., follower_count > 5000).

If Normal User:

The post is saved to the posts table.

A message is published to the Message Queue.

The Fan-out Service consumes the message and pushes the post's reference into the feeds table for each of the user's friends.

If Celebrity:

The post is saved to the posts table.

No other action is taken. The write operation ends here, avoiding any fan-out.

Read Path (Hybrid)

This path is now slightly more complex to assemble the final feed.

A user requests their feed.

The API Server initiates two parallel data-fetching operations:

Fetch Base Feed: It retrieves the user's pre-computed feed from the Cache (Redis) or the feeds table. This contains posts from all the non-celebrity friends they follow.

Fetch Celebrity Posts: It identifies the list of celebrities the user follows. It then directly queries the posts table for recent posts from that list of celebrity author_ids.

Merge & Sort: The API Server takes the two lists of posts (the base feed and the celebrity posts), merges them into a single list, and sorts the combined list by created_at in descending order.

Return Feed: The final, sorted list is returned to the user's client for rendering.

5. Conclusion: Benefits of the Hybrid Approach

This hybrid model provides the best of both worlds:

High Performance for All: Normal users continue to get low-latency, real-time feed updates.

Scalability & Stability: The system is protected from the "thundering herd" problem caused by celebrity posts, ensuring stability for the entire platform.

Efficient Resource Use: It avoids unnecessary, massive write operations, saving computational resources and costs.

By intelligently segmenting users and applying the appropriate strategy, this architecture can effectively serve a massive, diverse user base with a consistent and reliable experience.

