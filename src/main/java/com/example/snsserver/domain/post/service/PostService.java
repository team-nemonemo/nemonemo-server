package com.example.snsserver.domain.post.service;

import com.example.snsserver.common.service.BaseImageService;
import com.example.snsserver.domain.like.repository.LikeRepository;
import com.example.snsserver.domain.post.repository.PostRepository;
import com.example.snsserver.domain.auth.entity.Member;
import com.example.snsserver.domain.post.entity.Post;
import com.example.snsserver.dto.post.request.PostRequestDto;
import com.example.snsserver.dto.post.response.PostResponseDto;
import com.example.snsserver.domain.auth.service.MemberService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService extends BaseImageService {

    private final PostRepository postRepository;
    private final MemberService memberService;
    private final LikeRepository likeRepository;

    @Transactional
    public PostResponseDto createPost(PostRequestDto requestDto, MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberService.getMemberByUsername(username);
        if (member == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + username);
        }

        String filePath = uploadImage(file, "post");

        Post post = Post.builder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .filePath(filePath)
                .member(member)
                .createdAt(LocalDateTime.now())
                .build();

        Post savedPost = postRepository.save(post);
        return mapToResponseDto(savedPost);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getAllPosts() {
        return postRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));
        return mapToResponseDto(post);
    }

    @Transactional
    public PostResponseDto updatePost(Long postId, PostRequestDto requestDto, MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));

        if (!post.getMember().getUsername().equals(username)) {
            throw new SecurityException("본인의 게시물만 수정할 수 있습니다.");
        }

        String filePath = post.getFilePath();
        if (file != null && !file.isEmpty()) {
            deleteImage(filePath);
            filePath = uploadImage(file, "post");
        }

        post = post.toBuilder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .filePath(filePath)
                .build();

        postRepository.save(post);
        return mapToResponseDto(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물이 존재하지 않습니다."));

        if (!post.getMember().getUsername().equals(username)) {
            throw new SecurityException("본인의 게시물만 삭제할 수 있습니다.");
        }

        deleteImage(post.getFilePath());
        postRepository.delete(post);
    }

    private PostResponseDto mapToResponseDto(Post post) {
        return PostResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .filePath(post.getFilePath())
                .username(post.getMember().getUsername())
                .createdAt(post.getCreatedAt())
                .likeCount(likeRepository.countByPostId(post.getId()))
                .commentCount(post.getComments() != null ? post.getComments().size() : 0)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("Java temp directory: {}", System.getProperty("java.io.tmpdir"));
    }
}