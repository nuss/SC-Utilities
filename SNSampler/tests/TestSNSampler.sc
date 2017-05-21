TestSNSampler : UnitTest {
	classvar <testController, <>testModel;
	var sampler;

	*initClass {
		testModel = Ref();
		testController = SimpleController(testModel);
	}

	setUp {
		var server = Server.named.at(\test) ?? { Server(\test, NetAddr("127.0.0.1", 57111)) };
		sampler = SNSampler(tempo: 2, beatsPerBar: 7, numBars: 2, numBuffers: 7, server: server);
	}

	tearDown {
		sampler.clear;
	}

	test_init {
		this.assertEquals(sampler.server.addr.ip, "127.0.0.1", "the sampler's server IP address should be '127.0.0.1'");
		this.assertEquals(sampler.server.addr.port, 57111, "the sampler's server port should be 57111");
		this.assertEquals(sampler.clock.tempo, 2, "the clock's tempo should be 2");
		this.assertEquals(sampler.clock.seconds, sampler.clock.beats / 2 - sampler.server.latency, "the clock's seconds should be the clock's beats  divided by 2 minus the server's latency");
		this.assertEquals(sampler.numBuffers, 7, "sampler.numBuffers should equal 7");
		this.assertEquals(sampler.beatsPerBar, 7, "beatsPerBar should equal 7");
		testModel.value_(\start).changed(\sync);
	}

	test_start {
		this.bootServer(sampler.server);
		this.wait({ sampler.buffersAllocated }, "buffers should have been allocated within 5 seconds", 5);
		"sampler.isPlaying: %, server is running: %, sampler.buffers: %\n".postf(sampler.isPlaying, sampler.server.serverRunning, sampler.buffers);
		this.assertEquals(sampler.buffers[0].numFrames, sampler.server.sampleRate * 7 * 2, "the frames of an allocated buffer should equal Server.default.sampleRate * 7 * 2");
		this.assertEquals(sampler.buffers.collect(_.bufnum), [0, 1, 2, 3, 4, 5, 6], "the bufnums of the allocated buffers should equal [0, 1, 2, 3, 4, 5, 6]");
		this.wait({ sampler.isPlaying }, "sampler should be playing within 2 seconds", 10);
		sampler.server.quit;
	}
}