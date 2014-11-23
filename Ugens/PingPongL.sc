PingPongL {
	*ar { arg inputs, delayTime, feedback=0.7, rotate=1/*, numChannels=2*/;
		var buffer, delaySamps, phase, frames, feedbackChannels, delayedSignals;

		inputs.poll;

		buffer = LocalBuf(SampleRate.ir * 2, inputs.size);
		delaySamps = max(0, delayTime * SampleRate.ir - ControlDur.ir).round;
		frames = BufFrames.kr(buffer);
		phase = Phasor.ar(0, 1, 0, frames);

		feedbackChannels = LocalIn.ar(inputs.size) * feedback;

 		delayedSignals = BufRd.ar(inputs.size, buffer, (phase - delaySamps).wrap(0, frames), 0);
		LocalOut.ar(delayedSignals);

		BufWr.ar((inputs + feedbackChannels).rotate(rotate) <! delayedSignals.asArray.first, buffer, phase, 1);
		^delayedSignals
	}
}