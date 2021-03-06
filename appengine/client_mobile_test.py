"""
The MIT License

Copyright (c) 2007 Leah Culver

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

Example consumer. This is not recommended for production.
Instead, you'll want to create your own subclass of OAuthClient
or find one that works with your web framework.
"""

import httplib
import time
import hashlib
import oauth as oauth

# settings for the local test consumer
SERVER = 'localhost'
PORT = 8080

# fake urls for the test server (matches ones in server.py)
REQUEST_TOKEN_URL = 'https://eyuentest.appspot.com/authorize_access'
RESOURCE_URL = 'http://localhost:8080/protected_upload'

ACCESS_TOKEN_URL = 'https://eyuentest.appspot.com/access_token'
AUTHORIZATION_URL = 'https://eyuentest.appspot.com/authorize'
CALLBACK_URL = 'http://printer.example.com/request_token_ready'
# key and secret granted by the service provider for this consumer application - same as the MockOAuthDataStore
CONSUMER_KEY = 'RyR9MgVUSZ4AS74M'
CONSUMER_SECRET = 'urt8TH84eXnLhq43'

# example client using httplib with headers
class SimpleOAuthClient(oauth.OAuthClient):

	def __init__(self, server, port=httplib.HTTP_PORT, request_token_url='', access_token_url='', authorization_url=''):
		self.server = server
		self.port = port
		self.request_token_url = request_token_url
		self.access_token_url = access_token_url
		self.authorization_url = authorization_url
		self.connection = httplib.HTTPConnection("%s:%d" % (self.server, self.port))

	def fetch_request_token(self, oauth_request):
		# via headers
		# -> OAuthToken

		print oauth_request.to_url()	

		self.connection.request(oauth_request.http_method, oauth_request.to_url(), headers=oauth_request.to_header()) 
		response = self.connection.getresponse()

		print 'response: '
		print response.status
		print response.getheader('WWW-Authenticate')
		return oauth.OAuthToken.from_string(response.read())

	def fetch_access_token(self, oauth_request):
		# via headers
		# -> OAuthToken
		self.connection.request(oauth_request.http_method, self.access_token_url, headers=oauth_request.to_header()) 
		response = self.connection.getresponse()
		return oauth.OAuthToken.from_string(response.read())

	def authorize_token(self, oauth_request):
		# via url
		# -> typically just some okay response
		self.connection.request(oauth_request.http_method, oauth_request.to_url()) 
		response = self.connection.getresponse()
		return response.read()

	def access_resource(self, oauth_request):
		# via post body
		# -> some protected resources
		headers = {'Content-Type' :'application/x-www-form-urlencoded'}
		self.connection.request('POST', RESOURCE_URL, body=oauth_request.to_postdata(), headers=headers)
		response = self.connection.getresponse()
		return response.read()

def run_example():

	# setup
	print '** OAuth Python Library Example **'
	client = SimpleOAuthClient(SERVER, PORT, REQUEST_TOKEN_URL, ACCESS_TOKEN_URL, AUTHORIZATION_URL)
	consumer = oauth.OAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET)
	signature_method_plaintext = oauth.OAuthSignatureMethod_PLAINTEXT()
	signature_method_hmac_sha1 = oauth.OAuthSignatureMethod_HMAC_SHA1()
	pause()

	# get request token
	print '* Obtain a request token ...'
	pause()
	param = {}
	param['username'] = 'test4'
	password = hashlib.sha1('tester')
	param['password'] = password.hexdigest()

	oauth_request = oauth.OAuthRequest.from_consumer_and_token(consumer, callback=CALLBACK_URL, http_url=client.request_token_url, parameters=param)
	oauth_request.sign_request(signature_method_plaintext, consumer, None)
	print 'REQUEST (via headers)'
	print 'parameters: %s' % str(oauth_request.parameters)
	pause()
	token = client.fetch_request_token(oauth_request)

	# access some protected resources
	print '* Access protected resources ...'
	pause()
	parameters = {'username': 'test', 'longitude': '-120', 'latitude': '34.5', 'stressval': '200', 'comments': 'this is a comment123', 'version': 'version 12'}
	oauth_request = oauth.OAuthRequest.from_consumer_and_token(consumer, token=token, http_method='POST', http_url=RESOURCE_URL, parameters=parameters)
	oauth_request.sign_request(signature_method_hmac_sha1, consumer, token)
	print 'REQUEST (via post body)'
	print 'parameters: %s' % str(oauth_request.parameters)
	pause()
	params = client.access_resource(oauth_request)
	print 'GOT'
	print 'non-oauth parameters: %s' % params
	pause()

def pause():
	print ''
	time.sleep(1)

if __name__ == '__main__':
	run_example()
	print 'Done.'
