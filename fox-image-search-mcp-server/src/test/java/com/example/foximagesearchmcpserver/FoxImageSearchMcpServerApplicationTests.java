package com.example.foximagesearchmcpserver;

import com.example.foximagesearchmcpserver.tools.ImageSearchTool;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FoxImageSearchMcpServerApplicationTests {
	@Resource
	private ImageSearchTool imageSearchTool;
	@Test
	void contextLoads() {
		String result=imageSearchTool.searchImages("computer");
		Assertions.assertNotNull(result);
	}

}
