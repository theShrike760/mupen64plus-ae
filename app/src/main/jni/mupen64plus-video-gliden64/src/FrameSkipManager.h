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

#ifndef FRAME_SKIPPER_H
#define FRAME_SKIPPER_H

#include <chrono>

class FrameSkipManager
{
public:
	enum FrameSkipMode
	{
		AUTO,
		MANUAL
	};

	FrameSkipManager();
	void setSkips(int _type, int _max);
	void setTargetFPS(int _fps);
	bool willSkipNext();
	void update();

private:
	int m_skipType;
	int m_maxSkips;
	int m_targetFPS;
	int m_skipCounter;
	std::chrono::system_clock::time_point m_initialTime;
	unsigned int m_actualFrame;
};

extern FrameSkipManager frameSkipManager;

#endif

