TestSNSampler : UnitTest {
	var sampler;

	setUp {
		sampler = SNSampler(tempo: 2, beatsPerBar: 7, numBars: 2, numBuffers: 7);
	}

	tearDown {
		sampler.clear;
	}

	test_init {
		this.wait({ sampler.buffersAllocated }, "buffers should have been allocated within 5 seconds", 5);
		this.assertEquals(sampler.clock.tempo, 2, "the clock's tempo should be 2");
		this.assertEquals(sampler.clock.seconds, sampler.clock.beats / 2 - sampler.server.latency, "the clock's seconds should be the clock's beats  divided by 2 minus the server's latency");
		this.assertEquals(sampler.numBuffers, 7, "sampler.numBuffers should equal 7");
		this.assertEquals(sampler.beatsPerBar, 7, "beatsPerBar should equal 7");
		this.assertEquals(sampler.buffers[0].numFrames, Server.default.sampleRate * 7 * 2, "the frames of an allocated buffer should equal Server.default.sampleRate * 7 * 2");
		this.assertEquals(sampler.buffers.collect(_.bufnum), [0, 1, 2, 3, 4, 5, 6], "the bufnums of the allocated buffers should equal [0, 1, 2, 3, 4, 5, 6]");
	}
}