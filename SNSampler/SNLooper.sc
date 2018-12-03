SNLooper : AbstractSNSampler {
	classvar <all;
	var <name, <clock, <numBuffers, <bufLength, <numChannels, <server;
	var <buffers, recorder, <loopLengths;
	var <sample = false, samplingController, samplingModel, onTime, offTime;

	*new { |name, clock, numBuffers, bufLength, numChannels, server, oscFeedbackAddr|
		^super.newCopyArgs(name, clock ? TempoClock.default, numBuffers ? 5, bufLength ? 5, numChannels ? 1, server ? Server.default).init(oscFeedbackAddr);
	}

	init { |oscFeedbackAddr|
		[name, clock, numBuffers, bufLength, numChannels, server].postln;
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied, oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
		all ?? { all = () };
		if (name.isNil) {
			name = ("looper" + (all.size + 1)).asSymbol
		} {
			if (all.includesKey(name.asSymbol)) {
				Error("A looper under the name '%' already exists".format(name)).throw;
			}
		};
		all = all.put(name.asSymbol, this);
		server.waitForBoot {
			var in;
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, numChannels);
			loopLengths = nil ! numBuffers;
			server.sync;
			recorder = NodeProxy.audio(server, numChannels).pause;
			if (numChannels < 2) { in = 0 } { in = 0!numChannels };
			recorder[0] = {
				var soundIn = LeakDC.ar(SoundIn.ar(\in.kr(in))).scope("looper in");
				BufWr.ar(
					soundIn,
					\bufnum.kr(0),
					// Phasor will ramp from start to end and then jump back to start
					// if this.tempo != 1 we have to move through the buffer at a different rate
					Phasor.ar(\trig.tr(0).poll, BufRateScale.kr(\bufnum.kr(buffers[0].bufnum)), 0, BufFrames.kr(\bufnum.kr(0))).poll
				);
				// play silently
				Silent.ar;
			};
			this.cvCenterAddWidget("-start/stop", 0, #[0, 1, \lin, 1, 0], { |cv|
				var samplingActive = cv.value.asBoolean;
				if ((samplingActive and: { sample.not }).or(
					samplingActive.not and: { sample }
				)) {
					this.sample_(samplingActive)
				}
			}, 0, 0);
			this.cvCenterAddWidget("-in", 0, \in, { |cv|
				recorder.set(\in, cv.value)
			});
			this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0], { |cv|
				recorder.set(\bufnum, buffers[cv.value].bufnum);
			});
			this.prInitSampler;
		}
	}

	prInitSampler {
		samplingModel = Ref(sample);
		samplingController = SimpleController(samplingModel);
		samplingController.put(\value, { |changer, what|
			var length, nextBuf, bufIndex;
			sample = changer.value;
			if (sample) {
				onTime = Main.elapsedTime;
				recorder.resume;
				CVCenter.at((name ++ "-start/stop").asSymbol).value_(1);
			} {
				var bufnum;
				offTime = Main.elapsedTime;
				recorder.pause;
				// reset phasor
				recorder.set(\trig, 1);
				bufnum = recorder.get(\bufnum);
				bufIndex = buffers.detect{ |buf| buf.bufnum == bufnum }.bufnum;
				length = offTime - onTime;
				// "bufIndex: %\n".postf(bufIndex);
				if (length > bufLength) {
					loopLengths[bufIndex] = bufLength;
				} {
					loopLengths[bufIndex] = length;
				};
				nextBuf = bufIndex + 1 % numBuffers;
				// "next buffer: %\n".postf(nextBuf);
				recorder.set(\bufnum, buffers[nextBuf].bufnum);
				CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
				onTime = nil;
				CVCenter.at((name ++ "-start/stop").asSymbol).value_(0);
			}
		})
	}

	sample_ { |onOff|
		samplingModel.value_(onOff).changed(\value);
	}

	recorderSetIn { |in|
		CVCenter.at((name ++ "-in").asSymbol).value_(in.asInteger);
	}

	recorderSetBufnum { |bufnum|
		// recorder.set(\bufnum, bufnum);
		CVCenter.at((name ++ "-set bufnum").asSymbol).value_(bufnum);
	}


}