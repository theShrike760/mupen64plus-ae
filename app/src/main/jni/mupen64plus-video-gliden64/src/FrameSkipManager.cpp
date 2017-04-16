/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *   Copyright (C) 2011 yongzh (freeman.yong@gmail.com)                    *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.          *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

#include "FrameSkipManager.h"

FrameSkipManager frameSkipManager;


FrameSkipManager::FrameSkipManager()
		: m_skipType(AUTO), m_maxSkips(2), m_targetFPS(60), m_skipCounter(0), m_actualFrame(0)
{
}

void FrameSkipManager::setSkips(int type, int max)
{
	m_skipType = type;
	m_maxSkips = max;
}

void FrameSkipManager::setTargetFPS(int fps)
{
	m_targetFPS = fps;
}

bool FrameSkipManager::willSkipNext()
{
	return (m_skipCounter > 0);
}

void FrameSkipManager::update()
{
	if (m_maxSkips < 1) {
		// Frameskip disabled, do nothing
	} else if (m_skipType == MANUAL) {
		// Skip this frame based on a deterministic skip rate
		if (++m_skipCounter > m_maxSkips)
			m_skipCounter = 0;
	} else if (m_initialTime.time_since_epoch().count() > 0) // skipType == AUTO, running
	{
		// Compute the frame number we want be at, based on elapsed time and target FPS
		long long int elapsedMilliseconds =
				std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now() - m_initialTime).count();
		long long int desiredFrame = (elapsedMilliseconds * m_targetFPS) / 1000;

		// Record the frame number we are actually at
		m_actualFrame++;

		// See if we need to skip
		if (desiredFrame < m_actualFrame) {
			// We are ahead of schedule, so do nothing
		} else if (desiredFrame > m_actualFrame && m_skipCounter < m_maxSkips) {
			// We are behind schedule and we are allowed to skip this frame, so skip this frame
			m_skipCounter++;
		} else {
			// We are on schedule, or we are not allowed to skip this frame...
			// ... so do not skip this frame
			m_skipCounter = 0;
			// ... and pretend we are on schedule (if not already)
			m_actualFrame = static_cast<unsigned int>(desiredFrame);
		}
	} else // skipType == AUTO, initializing
	{
		// First frame, initialize auto-skip variables
		m_initialTime = std::chrono::system_clock::now();
		m_actualFrame = 0;
		m_skipCounter = 0;
	}
}
