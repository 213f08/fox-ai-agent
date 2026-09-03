package com.example.foxaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDownloadToolTest {

    @Test
    void downloadResource() {
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        String url = "https://ts1.tc.mm.bing.net/th/id/R-C.d3cd5a1b95416ae960efbc5e2d2ec702?rik=zU%2bcirCFjy0rYg&riu=http%3a%2f%2fy3.ifengimg.com%2fa%2f2014_52%2f2633f87e648cb10.jpg&ehk=DC4quUX3zSvpKlP6cWEl9xE3ehCJcpbRyAPs%2fBPY%2f3I%3d&risl=&pid=ImgRaw&r=0";
        String fileName = "logo.png";
        String result = resourceDownloadTool.downloadResource(url, fileName);
        assertNotNull(result);
        assertFalse(result.contains("下载资源失败"));
        assertTrue(result.contains("下载成功"));
    }
}